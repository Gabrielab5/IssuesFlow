# IssueFlow Frontend

A complete, production-ready React + TypeScript frontend for the IssueFlow ticket management system.

## Tech Stack

- **React 19** with TypeScript
- **Vite 8** for build tooling
- **Tailwind CSS 4** for styling
- **shadcn/ui** inspired components
- **React Router 7** for navigation
- **Axios** for API client
- **Recharts** for data visualization
- **dnd-kit** for drag-and-drop Kanban board
- **react-hook-form** + **zod** for form validation
- **react-datepicker** for date selection

## Features

### Authentication
- JWT Bearer token authentication
- Secure token storage in localStorage
- Automatic token injection via Axios interceptors
- Automatic logout on 401 errors
- Protected routes

### Dashboard
- Metrics overview with cards
- Ticket distribution charts (status & priority)
- Recent activity feed

### User Management (Admin Only)
- List, create, edit, and delete users
- Role-based badges

### Project Management
- Full CRUD operations
- Workload visualization with charts
- Soft-delete and restore (Admin)

### Ticket Management
- Interactive Kanban board with drag-and-drop
- Alternative list view
- Complete ticket details with comments & attachments

### Comments & Mentions
- Add comments with @mentions
- Mentions notification page

### File Attachments
- Upload, download, delete (max 10MB)

### CSV Import/Export
- Export and import tickets via CSV

### Audit Logs (Admin Only)
- Complete audit trail with filters and pagination

## Getting Started

### Prerequisites
- Node.js 18+
- IssueFlow backend running on `http://localhost:8080`

### Installation

```bash
cd frontend
npm install
npm run dev
```

App runs on `http://localhost:5173`

### Build for Production

```bash
npm run build
```

### Environment Variables

`.env` file:
```
VITE_API_BASE_URL=http://localhost:8080
```

## Default Credentials

- **Username:** admin
- **Password:** admin123

## Notes

- Requires backend connection (no mock data)
- Responsive design for all screen sizes
- Comprehensive error handling with toast notifications
