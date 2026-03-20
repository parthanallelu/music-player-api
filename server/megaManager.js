const mega = require('megajs');
const metadataManager = require('./metadataManager');

class MegaManager {
    constructor() {
        this.folderUrl = 'https://mega.nz/folder/A34yUK7B#bsc58SUGqm2fofpoHDkqyQ';
        this.songs = [];
        this.isInitialized = false;
    }

    async init() {
        if (this.isInitialized) return;
        try {
            console.log('Initializing MEGA Manager...');
            const folder = mega.File.fromURL(this.folderUrl);
            await folder.loadAttributes();

            await this._crawl(folder);

            // Batch enrichment with rate limiting
            console.log(`Enriching ${this.songs.length} MEGA songs...`);
            for (let i = 0; i < this.songs.length; i++) {
                this.songs[i] = await metadataManager.enrichMetadata(this.songs[i]);
                await new Promise(r => setTimeout(r, 200)); // API rate limit
            }

            // Save artist cache once after full batch (not per song)
            await metadataManager.saveArtistCache();

            this.isInitialized = true;
            console.log(`MEGA Manager ready. ${this.songs.length} songs indexed.`);
        } catch (error) {
            console.error('MEGA Init Error:', error.message);
        }
    }

    async _crawl(node, currentPath = []) {
        if (node.children) {
            for (const child of node.children) {
                await this._crawl(child, [...currentPath, node.name]);
            }
        } else if (node.name?.toLowerCase().endsWith('.mp3')) {
            // Structure: Genre/Artist/Song.mp3
            const parts = currentPath.filter(p => p && p !== 'Cloud Drive');
            const genre = parts[parts.length - 2] || 'Unknown';
            const artist = parts[parts.length - 1] || 'Unknown Artist';
            const title = node.name.replace(/\.mp3$/i, '');

            this.songs.push({
                id: `mega_${node.handle}`,
                title,
                artist,
                genre,
                album: '',
                year: 0,
                artistImage: '',
                source: 'mega',
                handle: node.handle,
                node,
            });
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
        return this.songs.find(s => s.handle === handle) || null;
    }
}

module.exports = new MegaManager();
