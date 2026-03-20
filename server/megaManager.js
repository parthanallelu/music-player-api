const mega = require('megajs');
const fs = require('fs-extra');
const path = require('path');
const metadataManager = require('./metadataManager');

const CACHE_FILE = path.join(__dirname, 'cache', 'songs.json');
fs.ensureDirSync(path.join(__dirname, 'cache'));

class MegaManager {
    constructor() {
        this.folderUrl = 'https://mega.nz/folder/A34yUK7B#bsc58SUGqm2fofpoHDkqyQ';
        this.songs = [];
        this.isInitialized = false;
        this.indexing = false;
        this.songs = []; // Ensure initialized as empty array // Added for tracking indexing status
    }

    async init() {
        if (this.indexing) return;
        this.indexing = true;
        console.log('Initializing MegaManager...');
        
        // Load from cache first for instant startup
        await this.loadCache();

        // Start background crawl and enrichment
        this._crawlAndEnrich().then(async () => {
            console.log('Background MEGA crawl completed.');
            await this.saveCache();
            this.isInitialized = true; // Mark as initialized after full crawl and save
            this.indexing = false;
            console.log(`MEGA Manager ready. ${this.songs.length} songs indexed.`);
        }).catch(err => {
            console.error('MEGA crawl failed:', err.message);
            this.indexing = false;
        });
    }

    async loadCache() {
        try {
            if (await fs.pathExists(CACHE_FILE)) {
                const cachedSongs = await fs.readJson(CACHE_FILE);
                // Filter out any corrupt entries from previous bugs
                this.songs = cachedSongs.filter(s => s.handle && s.handle !== 'undefined' && s.id !== 'mega_undefined');
                console.log(`Loaded ${this.songs.length} valid MEGA songs from cache.`);
            }
        } catch (err) {
            console.warn('Failed to load MEGA cache:', err.message);
        }
    }

    async saveCache() {
        try {
            // Save metadata but strip the 'node' object (too large/cyclic)
            const songsToCache = this.songs.map(({ node, ...rest }) => rest);
            await fs.writeJson(CACHE_FILE, songsToCache, { spaces: 2 });
            console.log('MEGA metadata cache saved.');
        } catch (err) {
            console.error('Failed to save MEGA cache:', err.message);
        }
    }

    async _crawlAndEnrich() {
        try {
            console.log('Starting MEGA folder crawl...');
            const folder = mega.File.fromURL(this.folderUrl);
            await folder.loadAttributes();
            
            this.songs = this.songs || []; // Keep existing songs if any
            const processedHandles = new Set();
            
            const crawl = async (node) => {
                if (node.children) {
                    for (const child of node.children) {
                        await crawl(child);
                    }
                } else if (node.name?.toLowerCase().endsWith('.mp3')) {
                    // Correctly extract mega handle
                    const handle = node.handle || (node.downloadId && node.downloadId[1]);
                    if (!handle) {
                        console.warn(`No handle found for node: ${node.name}`);
                        return;
                    }

                    if (processedHandles.has(handle)) return; // Avoid duplicates
                    processedHandles.add(handle);

                    const titleFromName = node.name.replace(/\.mp3$/i, '');
                    const [artistPart, songTitlePart] = titleFromName.includes(' - ') ? titleFromName.split(' - ') : ['Unknown Artist', titleFromName];
                    
                    const songId = `mega_${handle}`;
                    const existingIdx = this.songs.findIndex(s => s.id === songId);
                    const existing = existingIdx !== -1 ? this.songs[existingIdx] : null;
                    
                    const song = {
                        id: songId,
                        title: songTitlePart?.trim() || titleFromName.trim(),
                        artist: artistPart?.trim() || 'Unknown Artist',
                        genre: existing?.genre || '',
                        album: existing?.album || '',
                        year: existing?.year || 0,
                        duration: existing?.duration || 0,
                        artistImage: '',
                        source: 'mega',
                        handle: handle,
                        node,
                    };

                    const enriched = await metadataManager.enrichMetadata(song);
                    if (existingIdx !== -1) {
                        this.songs[existingIdx] = enriched;
                    } else {
                        this.songs.push(enriched);
                    }
                    await new Promise(r => setTimeout(r, 100)); // Minimal delay
                }
            };

            await crawl(folder);
            await metadataManager.saveArtistCache();
            console.log(`MEGA crawl and enrichment finished. ${this.songs.length} songs total.`);
        } catch (err) {
            console.error('MEGA _crawlAndEnrich Error:', err.message);
            throw err; // Re-throw to be caught by the .catch in init()
        }
    }

    /**
     * Returns MEGA songs with relative API URLs.
     * The caller is responsible for prepending the base URL.
     */
    getSongs() {
        return this.songs.map(({ node, handle, ...rest }) => ({
            ...rest,
            albumArtUrl: `/v1/mega-cover/${rest.id}`,
            streamUrl: `/v1/mega-stream/${rest.id}`,
        }));
    }

    getSongByHandle(handle) {
        console.log(`Searching for handle: ${handle}`);
        const song = this.songs.find(s => String(s.handle) === String(handle)) || null;
        if (!song) console.warn(`Handle NOT found. Inventory size: ${this.songs.length}`);
        return song;
    }
}

module.exports = new MegaManager();
