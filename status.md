# EduNet Project Status

## 1. Unified Application Server (Rust + React)
- **Single Process**: The Rust backend now automatically builds the React frontend via `build.rs` and embeds it into the compiled binary.
- **Serving**: Both the API (`/api/*`) and the React Single Page App (SPA) are served directly from `localhost:8080` by Actix Web. No need for a separate `npm run dev` server anymore.
- **React Router Support**: The backend contains a catch-all route so that React Router links (e.g. `/staff/dashboard`) work seamlessly on reload.

## 2. Database Integration
- **Platform**: Fully connected to **MongoDB Atlas** (cloud database).
- **Database Name**: `techathon`
- **Seeding**: Created a `seed_db.mjs` script that clears old data and populates 2 Teachers, 6 Students, 3 Subjects, and 12 Enrollments so that testing is immediate.

## 3. Authentication Flow
- **Role-based Auth**: `POST /api/auth/login` checks credentials against the Atlas DB.
- **Frontend Context**: `AuthContext` securely stores the logged-in user and role, persisting across page reloads.

## 4. Staff (Teacher) Portal
- **Dashboard**: Teachers can view classes they teach and create new Subjects.
- **Subject Details**:
  - Live creation of Assignments (file uploads supported).
  - Ability to Start / End Lab sessions.
  - Live Auto-polling (every 15s) of student submissions.
  - Export submissions to CSV.
  
## 5. Student Portal
- **Dashboard**: Students can see active subjects and currently running lab assignments.
- **Assignment View**:
  - Drag-and-drop file upload zone for solutions.
  - Live Countdown / Progress bar for the lab session.
  - Real-time submission status (auto-disables when "Submission Confirmed").
  - Auto-locking if the lab session is marked expired by the teacher.

## 6. Cleanup & Branding
- **Name Updated**: Changed from "Neuromesh" to "EduNet".
- **Branding Removed**: Stripped all "Lovable" default tags, meta-images, and dependencies from the React build pipeline.

---

## How to Run the App Now
Because of the new unified build system, you only need one command:

```powershell
# Stop any running instances first!
cargo run

# The app automatically opens your browser to:
# http://localhost:8080
```

*(Note: The `neuromesh.exe` you tried to run doesn't exist anymore because the project was renamed. If you want to run the raw executable directly, it is now located at `target/debug/edunet.exe`)*
