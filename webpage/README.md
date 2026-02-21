# EduNet — Lab Session Management System

A LAN-based lab session management portal for colleges. Built with:
- **Frontend**: React + Vite + shadcn/ui (TypeScript)
- **Backend**: Rust + Actix-web + MongoDB Atlas

## Running Locally

```bash
# Start Rust backend (port 8080)
cargo run

# In a separate terminal — start frontend (port 5173)
cd webpage && npm run dev
```

Open `http://localhost:5173`

## Seed Database

```bash
node seed_db.mjs
```

## Architecture

- Staff can create subjects, create/start/end assignments, view live submissions, export CSV
- Students see active lab sessions on their network and upload solutions
- All data stored in MongoDB Atlas (`techathon` database)
