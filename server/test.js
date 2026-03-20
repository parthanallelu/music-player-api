const axios = require("axios");
const CryptoJS = require("crypto-js");

const SAAVN_BASE = "https://www.jiosaavn.com/api.php";

function decryptUrl(encryptedUrl) {
    if (!encryptedUrl) return "";
    try {
        const key = CryptoJS.enc.Utf8.parse("38346591");
        const decrypted = CryptoJS.DES.decrypt({
            ciphertext: CryptoJS.enc.Base64.parse(encryptedUrl)
        }, key, {
            mode: CryptoJS.mode.ECB,
            padding: CryptoJS.pad.Pkcs7
        });
        const url = decrypted.toString(CryptoJS.enc.Utf8);
        return url.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4");
    } catch (err) {
        return "";
    }
}

function transformTrack(track) {
    let streamUrl = "";
    if (track.encrypted_media_url) {
        streamUrl = decryptUrl(track.encrypted_media_url);
    } else if (track.media_preview_url) {
        streamUrl = track.media_preview_url.replace("preview.saavncdn.com", "aac.saavncdn.com").replace("_96_p.mp4", "_320.mp4");
    }

    let albumArtUrl = track.image || track.albumArtUrl || "";
    if (albumArtUrl) {
        albumArtUrl = albumArtUrl.replace("150x150", "500x500");
    }

    let title = track.song || track.title || "Unknown";
    title = title.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&#039;/g, "'");

    let artist = track.singers || track.primary_artists || "Unknown Artist";
    artist = artist.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&#039;/g, "'");

    let album = track.album || track.album_name || "";
    let perma_url = track.perma_url || track.url || "";
    let play_count = track.play_count || "0";

    return {
        id: String(track.id),
        title: title,
        artist: artist,
        album: album,
        albumArtUrl: albumArtUrl,
        perma_url: perma_url,
        streamUrl: streamUrl,
        play_count: play_count,
        duration: (parseInt(track.duration) || 0) * 1000,
    };
}

async function test() {
    const query = "top hits";
    const response = await axios.get(SAAVN_BASE, {
        params: {
            __call: "search.getResults",
            q: query,
            n: 5,
            p: 1,
            _format: "json",
            _marker: 0,
            ctx: "android"
        }
    });

    console.log("Response Type:", typeof response.data);
    // JioSaavn sometimes wraps result or returns weird string with JSON
    let jsondata = response.data;
    if (typeof jsondata === 'string') {
        console.log("String response starts with:", jsondata.substring(0, 50));
        // Sometimes it has HTML comments or stuff before JSON
        try { jsondata = JSON.parse(jsondata); } catch (e) { console.log("JSON parse failed"); }
    }

    const tracks = jsondata.results || [];
    const songs = tracks.map(transformTrack).filter(s => s.streamUrl);
    console.log(JSON.stringify(songs, null, 2));
}

test();
