const mega = require('megajs');
const url = 'https://mega.nz/folder/A34yUK7B#bsc58SUGqm2fofpoHDkqyQ';

async function test() {
    try {
        console.log('Connecting to MEGA...');
        const folder = mega.File.fromURL(url);
        await folder.loadAttributes();
        console.log('Attributes loaded. Children count:', folder.children?.length);
        
        const findMp3 = (node) => {
            if (node.name?.toLowerCase().endsWith('.mp3')) return node;
            if (node.children) {
                for (const child of node.children) {
                    const found = findMp3(child);
                    if (found) return found;
                }
            }
            return null;
        };

        const file = findMp3(folder);
        if (file) {
            console.log('Node structure for:', file.name);
            console.log('Keys:', Object.keys(file));
        } else {
            console.log('No mp3 found in folder');
        }
    } catch (err) {
        console.error('Test error:', err.message);
    }
}

test().then(() => console.log('Test finished')).catch(e => console.error('Unhandled:', e.message));
