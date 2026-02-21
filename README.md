# EduNet Lab Portal  
LAN-Based Offline Practical Management System

EduNet Lab Portal is a LAN-based, fully offline college lab management system built on top of the EduNet / NeuroMesh architecture. It enables staff to conduct practical sessions and students to submit lab work using only a local WiFi network, without any internet or cloud dependency.

---

## Problem Statement

Most existing college lab management systems depend on the internet and centralized servers, making them unreliable in low-resource or poor-network environments. These limitations are common in regular college laboratories.

EduNet Lab Portal addresses this problem by providing a lightweight, offline-first solution that works entirely on a local network.

---

## Objectives

- Enable staff to manage practical sessions efficiently  
- Allow students to discover and join active lab sessions over LAN  
- Enforce time limits and submission deadlines  
- Operate reliably on low-end systems and weak networks  
- Eliminate the need for cloud services or internet access  

---

## Key Features

- Role-based authentication for staff and students  
- Subject-wise organization of lab work  
- Assignment and practical session creation by staff  
- Time-bound lab sessions with strict deadlines  
- Automatic discovery of active lab sessions over LAN  
- File upload with configurable format restrictions  
- Submission tracking with CSV export  
- Local MongoDB database  
- Optimized for low-resource college lab environments  

---

## High-Level Architecture

Staff PC (Host)  
- Rust Backend (EduNet)  
- MongoDB (Local)  
- UDP Broadcast for session discovery  
- Local lab storage directory  

Student PCs  
- Web frontend  
- LAN-based session discovery  
- File submission over LAN  

---

## Staff Workflow

1. Login as staff  
2. View subjects created by the staff member  
3. Open a subject  
4. Create an assignment by specifying:  
   - Assignment name  
   - Sample practical file  
   - Allowed file types (.cpp, .py, .java, .pdf)  
   - Time limit and submission deadline  
5. Start the lab session  
6. Monitor active session status and student submissions  
7. Export submission details as a CSV file  

---

## Student Workflow

1. Login as student  
2. View enrolled subjects  
3. Automatically discover active assignments on the local network  
4. Download the sample practical file  
5. Complete the task locally  
6. Upload the solution before the deadline  
7. Receive submission confirmation  

---

## Database Details

Database: MongoDB  
Database Name: neuromesh_lab  
Connection: mongodb://localhost:27017  

Collections used:  
- users  
- subjects  
- subject_enrollments  
- assignments  
- submissions  

MongoDB stores only metadata. All uploaded files are stored on disk.

---

## File Storage Structure

/lab_storage/  
- subject_<code>/  
  - assignment_<id>/  
    - sample.pdf  
    - submissions/  
      - <roll_no>_<timestamp>.cpp  
      - <roll_no>_<timestamp>.py  

---

## Technology Stack

### Backend
- Rust  
- EduNet / NeuroMesh Core  
- MongoDB (Rust driver)  
- UDP for session discovery  
- TCP / HTTP for APIs and file uploads  

### Frontend
- React with TypeScript  
- Vite  

---

## Network Ports

- UDP Discovery: 5000  
- Backend API: 7878  
- MongoDB: 27017  
- Ollama (optional): 11434  

---

## Setup Instructions

### Prerequisites
- MongoDB installed and running locally  
- Rust toolchain installed  
- Node.js installed  
- All systems connected to the same WiFi or LAN  

### Backend Setup
- Build the backend using Cargo  
- Run the compiled EduNet binary  
- Ensure MongoDB is running  

### Frontend Setup
- Install dependencies using npm  
- Start the development server  

---

## Security Model

- LAN-only access  
- Role-based authorization  
- File type validation  
- Strict deadline enforcement  
- No external network or cloud usage  

---

## Testing Checklist

- Staff can create subjects and assignments  
- Assignments become active when Start Lab is clicked  
- Students can discover active labs on LAN  
- Invalid file formats are rejected  
- Submissions are blocked after the deadline  
- Staff can view and export submissions  

---

## Use Cases

- College practical laboratories  
- Internal assessments  
- Offline computer labs  
- Low-resource educational institutions  
- Rural or limited-connectivity environments  

---

## Future Enhancements

- Attendance management  
- Automatic code evaluation  
- AI-assisted hints via NeuroMesh  
- Viva or oral examination mode  
- Plagiarism detection  
- Detailed analytics dashboard  

---

## Contributors

Aman Shaikh  
Wasim Pathan  
Omkar Gondkar
Atharva Mashale
