const axios = require('axios');
const fs = require('fs-extra');
const path = require('path');
const NodeCache = require('node-cache');
const mm = require('music-metadata');

const CACHE_DIR = path.join(__dirname, 'cache', 'covers');
const ARTIST_CACHE_FILE = path.join(__dirname, 'cache', 'artists.json');
const BUFFER_CACHE_DIR = path.join(__dirname, 'cache', 'buffers');
fs.ensureDirSync(CACHE_DIR);
fs.ensureDirSync(BUFFER_CACHE_DIR);

// In-memory cache with 24h TTL
const artistCache = new NodeCache({ stdTTL: 86400 });

// Normalized genre map
const GENRE_MAP = [
    { keywords: ['edm', 'dance'], label: 'EDM' },
    { keywords: ['techno', 'house'], label: 'Techno' },
    { keywords: ['rap', 'hip hop', 'hip-hop'], label: 'Rap' },
    { keywords: ['hindi', 'bollywood'], label: 'Hindi' },
    { keywords: ['electronic', 'synth'], label: 'Electronic' },
    { keywords: ['lofi', 'lo-fi', 'chill'], label: 'LoFi' },
    { keywords: ['rock', 'metal'], label: 'Rock' },
    { keywords: ['jazz', 'blues'], label: 'Jazz' },
    { keywords: ['r&b', 'soul'], label: 'R&B' },
];

class MetadataManager {
    constructor() {
        this._cacheLoaded = this._loadArtistCache();
    }

    async _loadArtistCache() {
        try {
            if (await fs.pathExists(ARTIST_CACHE_FILE)) {
                const data = await fs.readJson(ARTIST_CACHE_FILE);
                Object.entries(data).forEach(([k, v]) => artistCache.set(k, v));
                console.log(`Loaded ${Object.keys(data).length} cached artist profiles.`);
            }
        } catch (err) {
            console.warn('Artist cache load failed:', err.message);
        }
    }

    async saveArtistCache() {
        const keys = artistCache.keys();
        const data = {};
        keys.forEach(k => { data[k] = artistCache.get(k); });
        await fs.writeJson(ARTIST_CACHE_FILE, data);
    }

    normalizeGenre(genre) {
        if (!genre) return 'Pop';
        const g = genre.toLowerCase();
        for (const entry of GENRE_MAP) {
            if (entry.keywords.some(kw => g.includes(kw))) return entry.label;
        }
        return 'Pop';
    }

    async enrichMetadata(song) {
        console.log(`  Enriching: ${song.artist} — ${song.title}`);

        const enriched = {
            ...song,
            album: song.album || 'Unknown Album',
            year: song.year || 0,
            genre: this.normalizeGenre(song.genre),
            artistImage: '',
            duration: song.duration || 0,
        };

        // STEP 0: Get duration from MEGA if missing
        if (enriched.duration === 0 && song.node) {
            const megaDuration = await this.getDurationFromMega(song.node);
            if (megaDuration > 0) enriched.duration = Math.round(megaDuration * 1000);
        }

        // Use artist cache if available
        const cached = artistCache.get(song.artist);
        if (cached) {
            enriched.artistImage = cached.artistImage || '';
            if (enriched.genre === 'Pop' && cached.genre) {
                enriched.genre = cached.genre;
            }
        }

        try {
            // STEP 1: MusicBrainz — album name + release year
            const query = `artist:${song.artist} AND recording:${song.title}`;
            const mbUrl = `https://musicbrainz.org/ws/2/recording/?query=${encodeURIComponent(query)}&fmt=json`;
            const mbRes = await axios.get(mbUrl, {
                headers: { 'User-Agent': 'MusicPlayerEnrich/2.0' },
                timeout: 5000,
            });

            const recording = mbRes.data.recordings?.[0];
            if (recording) {
                const release = recording.releases?.[0];
                if (release) {
                    enriched.album = release.title || enriched.album;
                    if (release.date) {
                        enriched.year = parseInt(release.date.split('-')[0]) || enriched.year;
                    }

                    // Cover Art Archive
                    try {
                        const caaRes = await axios.get(
                            `https://coverartarchive.org/release/${release.id}`,
                            { timeout: 5000 }
                        );
                        enriched.albumArtUrl = caaRes.data.images?.[0]?.image || enriched.albumArtUrl;
                    } catch (_) { /* Cover not available — non-critical */ }
                }
            }

            // STEP 2: Last.fm — artist image + genre tags
            if (!enriched.artistImage || enriched.genre === 'Pop') {
                const LASTFM_KEY = process.env.LASTFM_API_KEY || '';
                if (LASTFM_KEY) {
                    const artistUrl = `https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=${encodeURIComponent(song.artist)}&api_key=${LASTFM_KEY}&format=json`;
                    const artistRes = await axios.get(artistUrl, { timeout: 5000 });
                    const info = artistRes.data.artist;

                    if (info) {
                        enriched.artistImage = info.image?.find(i => i.size === 'extralarge')?.['#text'] || '';
                        if (info.tags?.tag?.[0]) {
                            enriched.genre = this.normalizeGenre(info.tags.tag[0].name);
                        }

                        // Update in-memory cache (disk save happens after batch)
                        artistCache.set(song.artist, {
                            artistImage: enriched.artistImage,
                            genre: enriched.genre,
                        });
                    }
                }
            }
        } catch (error) {
            console.warn(`  Enrichment failed for "${song.title}":`, error.message);
        }

        return enriched;
    }

    async getCoverPath(songId) {
        const cachePath = path.join(CACHE_DIR, `${songId}.jpg`);
        return (await fs.pathExists(cachePath)) ? cachePath : null;
    }

    async getDurationFromMega(fileNode) {
        const songId = `mega_${fileNode.handle}`;
        const bufferPath = path.join(BUFFER_CACHE_DIR, `${songId}.bin`);

        try {
            // Check if we have this buffer cached
            if (await fs.pathExists(bufferPath)) {
                const buffer = await fs.readFile(bufferPath);
                const metadata = await mm.parseBuffer(buffer, { mimeType: 'audio/mpeg', size: fileNode.size });
                return metadata.format.duration || 0;
            }

            // Download first 1MB for metadata parsing and caching
            const stream = fileNode.download({ start: 0, end: 1024 * 1024 });
            const chunks = [];
            
            return new Promise((resolve) => {
                stream.on('data', chunk => chunks.push(chunk));
                stream.on('end', async () => {
                    try {
                        const buffer = Buffer.concat(chunks);
                        // Save to buffer cache for instant playback start
                        await fs.writeFile(bufferPath, buffer).catch(e => console.warn('Buffer save failed:', e.message));
                        
                        const metadata = await mm.parseBuffer(buffer, { mimeType: 'audio/mpeg', size: fileNode.size });
                        resolve(metadata.format.duration || 0);
                    } catch (e) {
                        resolve(0);
                    }
                });
                stream.on('error', () => resolve(0));
                
                // Safety timeout
                setTimeout(() => {
                    stream.destroy();
                    resolve(0);
                }, 15000);
            });
        } catch (err) {
            return 0;
        }
    }

    async getCachedBuffer(songId) {
        const bufferPath = path.join(BUFFER_CACHE_DIR, `${songId}.bin`);
        if (await fs.pathExists(bufferPath)) {
            return await fs.readFile(bufferPath);
        }
        return null;
    }
}

module.exports = new MetadataManager();
