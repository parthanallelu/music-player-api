# MusicPlayer Backend API

A Node.js/Express server that proxies **Jamendo API**, providing your Android music player with access to a huge free music catalog.

## Quick Start

### 1. Get Jamendo API Key (free)
1. Go to [developer.jamendo.com](https://developer.jamendo.com)
2. Create an account → Create an app
3. Copy your `client_id`

### 2. Setup
```bash
cd server
npm install
```

### 3. Configure
Edit `.env`:
```
JAMENDO_CLIENT_ID=your_actual_client_id_here
PORT=3000
```

### 4. Run
```bash
npm start
```

Server starts at `http://localhost:3000`

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/v1/songs` | Popular/trending songs |
| `GET` | `/v1/songs?q=rock` | Search songs |
| `GET` | `/v1/songs/:id` | Get song by ID |
| `GET` | `/v1/search?q=query` | Dedicated search |
| `GET` | `/v1/genres?genre=pop` | Browse by genre |
| `GET` | `/v1/artists/:name` | Get artist's tracks |

### Response Format
```json
{
  "songs": [
    {
      "id": "123",
      "title": "Song Name",
      "artist": "Artist Name",
      "albumArtUrl": "https://...",
      "streamUrl": "https://...mp3",
      "duration": 210000
    }
  ]
}
```

## Deploy to Render (Free)

1. Push `server/` folder to a GitHub repo
2. Go to [render.com](https://render.com) → New Web Service
3. Connect your repo
4. Settings:
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Environment Variables:** Add `JAMENDO_CLIENT_ID`
5. Deploy → get URL like `https://music-api.onrender.com`
6. Update Android app `Constants.kt`:
   ```kotlin
   const val BASE_URL = "https://music-api.onrender.com/v1/"
   ```
