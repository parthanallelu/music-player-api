// ──────────────────────────────────────────────────────────
//  MusicPlayer Backend v2.0
//  JioSaavn proxy · YouTube search · MEGA cloud streaming
// ──────────────────────────────────────────────────────────

require("dotenv").config();
const express = require("express");
const cors = require("cors");
const axios = require("axios");
const crypto = require("crypto-js");
const megaManager = require('./megaManager');
const metadataManager = require('./metadataManager');
const fs = require('fs-extra');
const path = require('path');
const ytSearch = require("yt-search");

const app = express();
const PORT = process.env.PORT || 3000;
const SAAVN_BASE = "https://www.jiosaavn.com/api.php";
const DES_KEY = crypto.enc.Utf8.parse("38346591");

app.use(cors());
app.use(express.json());

// ─── Input validation middleware ─────────────────────────
app.use('/v1', (req, res, next) => {
  for (const key of Object.keys(req.query)) {
    if (typeof req.query[key] === 'string' && req.query[key].length > 200) {
      return res.status(400).json({ error: 'Query parameter too long' });
    }
  }
  next();
});

// ─── Health check ────────────────────────────────────────
app.get("/", (req, res) => {
  res.json({
    status: "ok",
    service: "MusicPlayer API",
    version: "2.0.0",
    endpoints: [
      "GET /v1/songs",
      "GET /v1/songs?q=query",
      "GET /v1/songs/:id",
      "GET /v1/search?q=query",
      "GET /v1/recommendations",
      "GET /v1/genres?genre=name",
      "GET /v1/artists/:name",
    ],
  });
});

// ─── Helpers ─────────────────────────────────────────────

/** Decrypt JioSaavn encrypted media URL */
function decryptUrl(encryptedUrl) {
  if (!encryptedUrl) return "";
  try {
    const decrypted = crypto.DES.decrypt(
      { ciphertext: crypto.enc.Base64.parse(encryptedUrl) },
      DES_KEY,
      { mode: crypto.mode.ECB, padding: crypto.pad.Pkcs7 }
    );
    return decrypted
      .toString(crypto.enc.Utf8)
      .replace("_96.mp4", "_320.mp4")
      .replace("_160.mp4", "_320.mp4");
  } catch (err) {
    console.error("Decryption failed:", err.message);
    return "";
  }
}

/** Decode common HTML entities from JioSaavn responses */
function decodeEntities(str) {
  return str.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&#039;/g, "'");
}

/** Safely parse JioSaavn response — sometimes returns string with HTML prefix instead of JSON */
function safeParseSaavn(responseData) {
  if (typeof responseData === 'object' && responseData !== null) return responseData;
  if (typeof responseData === 'string') {
    // Strip any HTML comments or non-JSON prefix
    const jsonStart = responseData.indexOf('{');
    if (jsonStart >= 0) {
      try {
        return JSON.parse(responseData.substring(jsonStart));
      } catch (e) {
        console.warn('JioSaavn response JSON parse failed:', e.message);
      }
    }
  }
  return {};
}

/** Transform a raw JioSaavn track into the app Song model */
function transformTrack(track) {
  let streamUrl = "";
  if (track.encrypted_media_url) {
    streamUrl = decryptUrl(track.encrypted_media_url);
  if (track.media_preview_url) {
    const previewUrl = track.media_preview_url
      .replace("preview.saavncdn.com", "aac.saavncdn.com")
      .replace("_96_p.mp4", "_320.mp4");
    // Only return if it's not a known broken pattern (some English songs use different CDN)
    if (!streamUrl) streamUrl = previewUrl;
  }

  let albumArtUrl = track.image || track.albumArtUrl || "";
  if (albumArtUrl) albumArtUrl = albumArtUrl.replace("150x150", "500x500");

  return {
    id: String(track.id),
    title: decodeEntities(track.song || track.title || "Unknown"),
    artist: decodeEntities(track.singers || track.primary_artists || "Unknown Artist"),
    album: track.album || track.album_name || "",
    albumArtUrl,
    perma_url: track.perma_url || track.url || "",
    streamUrl,
    play_count: track.play_count || "0",
    duration: (parseInt(track.duration) || 0) * 1000,
    source: "jiosaavn",
    genre: track.language || track.type || "unknown",
    year: parseInt(track.year) || 0,
    artistImage: "",
  };
}

/** Resolve relative MEGA URLs to absolute using the request's host */
function resolveMegaUrls(songs, baseUrl) {
  return songs.map(s => ({
    ...s,
    albumArtUrl: `${baseUrl}${s.albumArtUrl}`,
    streamUrl: `${baseUrl}${s.streamUrl}`,
  }));
}

/** Deduplicate songs by ID */
function deduplicateSongs(songs) {
  const seen = new Set();
  return songs.filter(s => {
    if (seen.has(s.id)) return false;
    seen.add(s.id);
    return true;
  });
}

// ─── GET /v1/songs ───────────────────────────────────────
app.get("/v1/songs", async (req, res) => {
  try {
    const query = req.query.q || "top hits";

    const response = await axios.get(SAAVN_BASE, {
      params: {
        __call: "search.getResults",
        q: query,
        n: req.query.limit || 50,
        p: 1,
        _format: "json",
        _marker: 0,
        ctx: "android",
      },
    });

    const data = safeParseSaavn(response.data);
    const tracks = data.results || [];
    let songs = tracks.map(transformTrack).filter(s => s.streamUrl);

    // Merge MEGA songs on general/trending queries
    if (!req.query.q || req.query.q === "top hits") {
      const baseUrl = `${req.protocol}://${req.get('host')}`;
      const megaSongs = resolveMegaUrls(megaManager.getSongs(), baseUrl);
      songs = [...megaSongs, ...songs];
    }

    res.json({ songs });
  } catch (error) {
    console.error("Error fetching songs:", error.message);
    res.status(500).json({ error: "Failed to fetch songs", songs: [] });
  }
});

// ─── GET /v1/songs/:id ───────────────────────────────────
app.get("/v1/songs/:id", async (req, res) => {
  try {
    const response = await axios.get(SAAVN_BASE, {
      params: {
        __call: "song.getDetails",
        pids: req.params.id,
        _format: "json",
        _marker: 0,
        ctx: "android",
      },
    });

    const data = safeParseSaavn(response.data);
    const track = data[req.params.id];
    if (!track) return res.status(404).json({ error: "Song not found" });

    res.json(transformTrack(track));
  } catch (error) {
    console.error("Error fetching song:", error.message);
    res.status(500).json({ error: "Failed to fetch song" });
  }
});

// ─── GET /v1/search?q=query ──────────────────────────────
app.get("/v1/search", async (req, res) => {
  try {
    const query = req.query.q;
    const page = parseInt(req.query.page) || 1;
    const limit = parseInt(req.query.limit) || 20;

    if (!query || !query.trim()) return res.json({ songs: [] });

    // 1. Fetch from JioSaavn
    let jioSaavnSongs = [];
    try {
      const response = await axios.get(SAAVN_BASE, {
        params: {
          __call: "search.getResults",
          q: query.trim(),
          n: limit,
          p: page,
          _format: "json",
          _marker: 0,
          ctx: "android",
        },
      });
      const data = safeParseSaavn(response.data);
      jioSaavnSongs = (data.results || [])
        .map(transformTrack)
        .filter(s => s.streamUrl);
    } catch (e) {
      console.error("JioSaavn search failed:", e.message);
    }

    // 2. Fetch from YouTube
    let youtubeSongs = [];
    try {
      const ytResult = await ytSearch(query.trim());
      const startIdx = (page - 1) * limit;
      const vids = ytResult.videos.slice(startIdx, startIdx + limit);
      youtubeSongs = vids.map(v => ({
        id: v.videoId,
        title: v.title,
        artist: v.author.name,
        album: "YouTube",
        albumArtUrl: v.thumbnail,
        perma_url: v.url,
        streamUrl: "", // Empty string instead of null for non-nullable Android field
        play_count: (v.views || 0).toString(),
        duration: v.seconds * 1000,
        source: "youtube",
        genre: "unknown",
        year: 0,
        artistImage: "",
      }));
    } catch (e) {
      console.error("YouTube search failed:", e.message);
    }

    // Search MEGA songs
    const lowQuery = query.toLowerCase();
    const megaSongs = megaManager.getSongs()
      .filter(s => 
        s.title.toLowerCase().includes(lowQuery) || 
        s.artist.toLowerCase().includes(lowQuery) ||
        (s.album && s.album.toLowerCase().includes(lowQuery))
      )
      .map(s => ({ ...s, source: "mega" }));

    res.json({ songs: [...megaSongs, ...jioSaavnSongs, ...youtubeSongs] });
  } catch (error) {
    console.error("Search error:", error.message);
    res.status(500).json({ error: "Search failed", songs: [] });
  }
});

// ─── GET /v1/recommendations ─────────────────────────────
app.get("/v1/recommendations", async (req, res) => {
  try {
    const genres = (req.query.genres || "").split(",").map(g => g.trim().toLowerCase()).filter(Boolean);
    const artists = (req.query.artists || "").split(",").map(a => a.trim().toLowerCase()).filter(Boolean);

    // 1. Fetch seed songs in parallel
    const seedQueries = ["top hits", ...genres.slice(0, 2)];
    const results = await Promise.all(
      seedQueries.map(q =>
        axios.get(SAAVN_BASE, {
          params: { __call: "search.getResults", q, n: 20, p: 1, _format: "json", _marker: 0, ctx: "android" },
        })
      )
    );

    let allSongs = results.flatMap(r => {
      const data = safeParseSaavn(r.data);
      return (data.results || []).map(transformTrack).filter(s => s.streamUrl);
    });

    // Add MEGA songs
    const baseUrl = `${req.protocol}://${req.get('host')}`;
    allSongs = [...allSongs, ...resolveMegaUrls(megaManager.getSongs(), baseUrl)];

    // Deduplicate
    const uniqueSongs = deduplicateSongs(allSongs);
    if (uniqueSongs.length === 0) return res.json({ songs: [] });

    // 2. Score songs (safe reduce instead of spread)
    const maxPlayCount = uniqueSongs.reduce(
      (max, s) => Math.max(max, parseInt(s.play_count) || 1), 1
    );

    const scoredSongs = uniqueSongs
      .map(song => {
        const genreMatch = genres.some(g => song.genre.toLowerCase().includes(g)) ? 1 : 0;
        const artistMatch = artists.some(a => song.artist.toLowerCase().includes(a)) ? 1 : 0;
        const popularity = (parseInt(song.play_count) || 0) / maxPlayCount;
        const score = (genreMatch * 0.5) + (artistMatch * 0.3) + (popularity * 0.2);
        return { ...song, score: parseFloat(score.toFixed(4)) };
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, 20);

    res.json({ songs: scoredSongs });
  } catch (error) {
    console.error("Recommendations error:", error.message);
    res.status(500).json({ error: "Failed to get recommendations", songs: [] });
  }
});

// ─── MEGA Endpoints ──────────────────────────────────────

app.get("/v1/mega-stream/:id", async (req, res) => {
  try {
    const handle = req.params.id.replace('mega_', '');
    const song = megaManager.getSongByHandle(handle);
    if (!song) return res.status(404).send('Song not found');

    const stream = song.node.download();

    // Handle stream errors before they become unhandled exceptions
    stream.on('error', (err) => {
      console.error('MEGA stream error:', err.message);
      if (!res.headersSent) res.status(500).send('Stream error');
    });

    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('Accept-Ranges', 'bytes');
    stream.pipe(res);
  } catch (error) {
    console.error('Mega Stream Error:', error.message);
    if (!res.headersSent) res.status(500).send('Streaming failed');
  }
});

app.get("/v1/mega-cover/:id", async (req, res) => {
  try {
    const handle = req.params.id.replace('mega_', '');
    const song = megaManager.getSongByHandle(handle);
    if (!song) return res.status(404).send('Not found');

    const coverPath = await metadataManager.getCoverPath(song.id);
    if (coverPath && await fs.pathExists(coverPath)) {
      res.sendFile(coverPath);
    } else {
      res.status(404).send('Cover not found');
    }
  } catch (error) {
    console.error('Cover error:', error.message);
    res.status(500).send('Cover error');
  }
});

// ─── GET /v1/genres ──────────────────────────────────────
app.get("/v1/genres", async (req, res) => {
  try {
    const genre = req.query.genre || "electronic";
    const response = await axios.get(SAAVN_BASE, {
      params: {
        __call: "search.getResults",
        q: genre + " hits",
        n: req.query.limit || 50,
        p: 1,
        _format: "json",
        _marker: 0,
        ctx: "android",
      },
    });

    const data = safeParseSaavn(response.data);
    const songs = (data.results || []).map(transformTrack).filter(s => s.streamUrl);
    res.json({ songs });
  } catch (error) {
    console.error("Genre fetch error:", error.message);
    res.status(500).json({ error: "Failed to fetch genre", songs: [] });
  }
});

// ─── GET /v1/artists/:name ───────────────────────────────
app.get("/v1/artists/:name", async (req, res) => {
  try {
    const response = await axios.get(SAAVN_BASE, {
      params: {
        __call: "search.getResults",
        q: req.params.name,
        n: req.query.limit || 50,
        p: 1,
        _format: "json",
        _marker: 0,
        ctx: "android",
      },
    });

    const data = safeParseSaavn(response.data);
    const songs = (data.results || []).map(transformTrack).filter(s => s.streamUrl);
    res.json({
      artist: {
        id: req.params.name,
        name: req.params.name,
        image: songs.length > 0 ? songs[0].albumArtUrl : "",
      },
      songs,
    });
  } catch (error) {
    console.error("Artist fetch error:", error.message);
    res.status(500).json({ error: "Failed to fetch artist", songs: [] });
  }
});

// ─── Keep-alive (Render free tier) ───────────────────────
const RENDER_URL = process.env.RENDER_EXTERNAL_URL || `http://localhost:${PORT}`;
const PING_INTERVAL = 14 * 60 * 1000;

function startKeepAlive() {
  setInterval(async () => {
    try {
      await axios.get(`${RENDER_URL}/`);
      console.log(`🏓 Keep-alive ping at ${new Date().toISOString()}`);
    } catch (err) {
      console.error("🏓 Keep-alive failed:", err.message);
    }
  }, PING_INTERVAL);
  console.log(`🏓 Keep-alive enabled — pinging every 14 min`);
}

// ─── Start server ────────────────────────────────────────
app.listen(PORT, async () => {
  console.log(`\n🎵  MusicPlayer API v2.0 running on port ${PORT}`);
  console.log(`    Health:  http://localhost:${PORT}/`);
  console.log(`    Songs:   http://localhost:${PORT}/v1/songs`);
  console.log(`    Search:  http://localhost:${PORT}/v1/search?q=tum%20hi%20ho`);

  if (process.env.RENDER) startKeepAlive();
  // Non-blocking: MEGA songs will appear once indexing completes
  megaManager.init().catch(err => console.error('MEGA init failed:', err.message));
});
