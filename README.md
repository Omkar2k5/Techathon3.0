# EduNet

A high-performance, distributed peer-to-peer networking framework built. This project enables seamless node discovery, secure local network communication, and decentralized state synchronization. 

## Features

- **Zero-Configuration Networking**: Automatic peer discovery on local networks via UDP broadcasts.
- **Reliable State Synchronization**: TCP-based communication layer for consistent data replication across connected nodes.
- **Decentralized Architecture**: No central server required; all participating nodes can handle network tasks dynamically.
- **Cross-Platform**: Compiled to a single standalone executable with an embedded management dashboard.

## Prerequisites

- Windows OS (for the included setup scripts)
- Local Network access (LAN/WLAN)

## Setup and Installation

1. **Download/Build**: Get the latest binary or build directly from the source.
2. **Configure Network**: Run `fix-firewall.bat` as an Administrator to allow peer-to-peer traffic.
3. **Start Node**: Run the main executable (`edunet.bat` or `edunet.exe`).
4. **Dashboard**: Navigate to the local dashboard (default `http://localhost:3000`) to view network topology and connected peers.

## Network Configuration (Manual)

If you prefer to configure your firewall manually instead of using the provided script, open an Administrator command prompt and run:

```cmd
netsh advfirewall firewall add rule name="Node TCP" dir=in action=allow protocol=TCP localport=7878
netsh advfirewall firewall add rule name="Node UDP" dir=in action=allow protocol=UDP localport=5000
```

## Security Note

This application automatically discovers and connects to other instances running on the same local network subnet. Only run this software on trusted networks.

## Building from Source

Ensure you have Rust and Cargo installed, as well as Node.js for the frontend interface.

```bash
# Build the application
cargo build --release
```

## License
MIT License