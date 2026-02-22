# EduNet: Decentralized Local-Network LMS with Offline AI

EduNet is a next-generation Learning Management System (LMS) designed for constrained environments. It functions as a complete unified web application for academic lab management, augmented with a peer-to-peer (P2P) network that democratizes access to Large Language Models (LLMs) over a local area network (LAN) without requiring active internet connections.

## Core Features

- **Offline Artificial Intelligence:** Students can access powerful AI coding assistants (via Ollama) even if their own machines lack the hardware to run them. The system automatically discovers powerful peers on the LAN and proxies LLM requests to them.
- **Zero-Config Peer Discovery:** Uses UDP broadcasting to announce LLM capabilities and discover LAN peers instantly.
- **Unified Binary:** The React frontend is compiled and embedded directly into the Rust backend executable. A single `cargo run` starts the entire platform.
- **Teacher Portal:** 
  - Create subjects and assignments with specific file-type constraints.
  - Set both Hard Deadlines (server cutoffs) and Personal Time Limits (countdown after opening).
  - Enroll students by roll number.
  - Monitor submissions in real-time and export them to CSV.
- **Student Portal:**
  - Join subjects via a unique subject code.
  - View active campus-wide labs and personal assignments.
  - Chat with the AI assistant.
  - See active network peers (showing hardware availability).
  - Upload solutions securely and view complete submission history.
  - Download previously submitted code directly from the Rust backend for review.

## Technology Stack

- **Backend:** Rust (Actix-Web, Serde, Tokio)
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, shadcn/ui
- **Database:** MongoDB Atlas
- **AI Integration:** Ollama (deepseek-coder-v2 for teachers, phi3-fast for students)
- **Networking:** Custom UDP Discovery Protocol + HTTP Proxying

## Setup and Installation

### Prerequisites
- [Rust & Cargo](https://rustup.rs/) (for the backend and build system)
- [Node.js & npm](https://nodejs.org/) (for building the frontend)
- [MongoDB Atlas](https://www.mongodb.com/cloud/atlas) account (or local MongoDB)
- [Ollama](https://ollama.com/) (optional, required only for nodes acting as AI providers)

### Configuration

1. **Database:** Edit `src/lab_module/mongo_connection.rs` to include your MongoDB URI.
2. **AI Models:** If you are running an AI provider node, make sure Ollama is running and you have pulled the required models:
   ```bash
   ollama run deepseek-coder-v2:16b
   ollama run phi3-fast:latest
   ```

### Building and Running

Because the frontend build is integrated into the Rust `build.rs` script, you only need one command to build and run the entire application:

```bash
cargo run
```

This will:
1. Trigger `npm run build` in the `webpage/` directory.
2. Embed the static assets into the Rust binary.
3. Start the Actix-Web server on `10.75.236.195:8080`.
4. Start UDP broadcast discovery on port `5000`.

Open your browser and navigate to `http://localhost:8080`.

## Network Settings (Windows)

For the P2P LLM discovery to work across physical machines, ensure your firewall allows inbound traffic. You can run the provided payload script or manually add rules:

```cmd
netsh advfirewall firewall add rule name="EduNet Web" dir=in action=allow protocol=TCP localport=8080
netsh advfirewall firewall add rule name="EduNet UDP" dir=in action=allow protocol=UDP localport=5000
```

## Security Note

This application automatically discovers and connects to other instances running on the same local network subnet. It is designed for use in trusted academic or corporate LANs.

## License
MIT License