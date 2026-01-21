# Call Recorder Architecture - Visual Documentation

## System Architecture Overview

Complete visual diagrams and architecture documentation available at:
https://github.com/Rajanabalakrishna/call_recorder/blob/eren/docs/ARCHITECTURE_DIAGRAM.md

## Diagrams Included:

1. **System Architecture** - Complete layered architecture diagram
2. **Call Detection Flow** - How incoming calls are detected and recorded
3. **Accessibility Service Flow** - VoIP and phone app detection process
4. **Permission & Service Lifecycle** - Full app lifecycle management
5. **Data Flow Diagram** - How data moves through the system
6. **Android Version Compatibility Matrix** - API levels and features

## Architecture Layers:

### Layer 1: Call Detection
- TelephonyManager for system calls
- Accessibility Service for VoIP apps
- Receiver for broadcasts

### Layer 2: Service Management
- Extract phone number and call type
- Determine recording parameters
- Start/stop recording service

### Layer 3: Foreground Service
- Persistent notification
- Lifecycle management
- Survives app close

### Layer 4: MediaRecorder
- Optimal AudioSource selection
- Recording configuration
- State management (prepare, start, stop)

### Layer 5: Storage
- App-specific directory
- File organization
- Database entries

### Layer 6: Flutter UI
- Display recordings
- Playback controls
- Settings management

For complete ASCII diagrams and detailed flows, see the full documentation file.