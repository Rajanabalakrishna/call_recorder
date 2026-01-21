# Quick Commands Reference

## 🛎 One-Line Pull Commands

### Fast Setup (Copy & Paste)

```bash
# If repo not cloned yet:
git clone https://github.com/Rajanabalakrishna/call_recorder.git && cd call_recorder && git fetch origin && git checkout eren && git pull origin eren

# If repo already cloned:
cd call_recorder && git fetch origin && git checkout eren && git pull origin eren
```

---

## 📥 Essential Pull Commands

### 1. Clone Repository (First Time)
```bash
git clone https://github.com/Rajanabalakrishna/call_recorder.git
cd call_recorder
```

### 2. Switch to eren Branch
```bash
git fetch origin
git checkout eren
```

Or in one command:
```bash
git checkout -b eren origin/eren
```

### 3. Pull Latest Changes
```bash
git pull origin eren
```

### 4. Verify Files
```bash
git log --oneline -5
ls -la docs/
```

---

## 📜 View Documentation Files

### View in Terminal
```bash
# Quick overview
cat START_HERE.md

# Implementation guide (follow this)
cat docs/STEP_BY_STEP_IMPLEMENTATION.md

# Troubleshooting
cat QUICK_REFERENCE_GUIDE.md

# Full technical documentation
cat docs/CALL_RECORDER_FIX_DOCUMENTATION.md

# Architecture diagrams
cat docs/ARCHITECTURE_DIAGRAM.md
```

### Open in Code Editor
```bash
# VS Code
code START_HERE.md
code docs/STEP_BY_STEP_IMPLEMENTATION.md

# vim/nano
vim START_HERE.md
nano docs/STEP_BY_STEP_IMPLEMENTATION.md
```

### View on GitHub
```bash
# Branch overview
https://github.com/Rajanabalakrishna/call_recorder/tree/eren

# Specific file
https://github.com/Rajanabalakrishna/call_recorder/blob/eren/START_HERE.md
https://github.com/Rajanabalakrishna/call_recorder/blob/eren/docs/STEP_BY_STEP_IMPLEMENTATION.md
```

---

## 🚀 Setup Project

### 1. Create Directories
```bash
mkdir -p android/app/src/main/kotlin/com/example/recorder/services
mkdir -p android/app/src/main/kotlin/com/example/recorder/receivers
mkdir -p android/app/src/main/res/xml
```

### 2. Follow Implementation Guide
```bash
# Read the step-by-step guide
cat docs/STEP_BY_STEP_IMPLEMENTATION.md

# Then follow each phase
# Phase 1: Setup (15 min)
# Phase 2: Services (15 min)
# Phase 3: Update files (5 min)
# Phase 4: Build & test (5 min)
# Phase 5: Enable accessibility (5 min)
```

### 3. Build Flutter App
```bash
# Clean build
flutter clean

# Get packages
flutter pub get

# Run in release mode
flutter run --release
```

---

## 🔍 Git Status & Info Commands

### Check Current Branch
```bash
git branch
# Should show: * eren
```

### Check Remote
```bash
git remote -v
# Should show origin pointing to your repo
```

### View Recent Commits
```bash
git log --oneline -10
# Shows last 10 commits

git log --graph --oneline --all
# Shows commit graph
```

### Check File Status
```bash
git status
# Shows modified/new files

ls -la docs/
# Lists all documentation files
```

---

## 📄 List All Documentation Files

```bash
# List all markdown files
find . -name "*.md" -type f

# Or with details
ls -lah *.md docs/*.md

# Count total lines
wc -l docs/*.md *.md
```

---

## 📁 File Reference

### Root Level
- `START_HERE.md` - Begin here!
- `README_IMPLEMENTATION.md` - Quick overview
- `QUICK_REFERENCE_GUIDE.md` - Snippets & troubleshooting
- `PULL_AND_SETUP_GUIDE.md` - This guide
- `COMMANDS.md` - Command reference
- `IMPLEMENTATION_NOTES.txt` - Quick notes

### docs/ Directory
- `CALL_RECORDER_FIX_DOCUMENTATION.md` - Technical guide (876 lines)
- `STEP_BY_STEP_IMPLEMENTATION.md` - Implementation guide (783 lines)
- `ADVANCED_TROUBLESHOOTING_AND_OPTIMIZATION.md` - Advanced fixes (656 lines)
- `ARCHITECTURE_DIAGRAM.md` - Diagrams (447 lines)
- `FULL_IMPLEMENTATION_BUNDLE.md` - Overview

---

## 📄 Combined Commands

### Pull & View All Files
```bash
cd call_recorder
git checkout eren
git pull origin eren
echo "\n=== Documentation Files ===\n"
ls -la *.md docs/*.md
echo "\n=== Start Here ===\n"
head -50 START_HERE.md
```

### Setup & Build
```bash
# Pull code
git checkout eren && git pull origin eren

# Create directories
mkdir -p android/app/src/main/kotlin/com/example/recorder/{services,receivers}
mkdir -p android/app/src/main/res/xml

# Build
flutter clean && flutter pub get && flutter run --release
```

### Full Setup from Scratch
```bash
# Clone repo
git clone https://github.com/Rajanabalakrishna/call_recorder.git
cd call_recorder

# Switch to eren
git fetch origin && git checkout eren

# Pull latest
git pull origin eren

# Create dirs
mkdir -p android/app/src/main/kotlin/com/example/recorder/{services,receivers}
mkdir -p android/app/src/main/res/xml

# View instructions
echo "Next: Read START_HERE.md"
cat START_HERE.md

# Then follow docs/STEP_BY_STEP_IMPLEMENTATION.md
```

---

## 📚 Documentation Reading Order

### Quickest Path (30 min)
1. `START_HERE.md` (5 min)
2. `docs/STEP_BY_STEP_IMPLEMENTATION.md` - Copy code (25 min)

### Complete Path (2 hours)
1. `START_HERE.md` (5 min)
2. `README_IMPLEMENTATION.md` (10 min)
3. `docs/CALL_RECORDER_FIX_DOCUMENTATION.md` (30 min)
4. `docs/ARCHITECTURE_DIAGRAM.md` (15 min)
5. `docs/STEP_BY_STEP_IMPLEMENTATION.md` - Implement (45 min)
6. `QUICK_REFERENCE_GUIDE.md` - Reference (as needed)

---

## ✅ Verification

### Verify Pull Success
```bash
git status
# Should show: On branch eren, nothing to commit

git branch
# Should show: * eren

ls docs/ | wc -l
# Should show: 6 (6 files in docs/)
```

### Verify Files Exist
```bash
# Quick check
test -f START_HERE.md && echo "✓ START_HERE.md" || echo "✗ Missing"
test -f docs/STEP_BY_STEP_IMPLEMENTATION.md && echo "✓ Implementation guide" || echo "✗ Missing"
test -f QUICK_REFERENCE_GUIDE.md && echo "✓ Quick reference" || echo "✗ Missing"
test -f docs/ARCHITECTURE_DIAGRAM.md && echo "✓ Architecture" || echo "✗ Missing"
```

---

## 🛠 Troubleshooting

### Branch Not Found
```bash
git fetch origin
git checkout -b eren origin/eren
```

### Already on branch but need to sync
```bash
git fetch origin
git reset --hard origin/eren
```

### Push Your Changes
```bash
git add .
git commit -m "Your message"
git push origin eren
```

### Update from Remote
```bash
git fetch origin
git merge origin/eren
```

---

## 🎉 Success Checklist

- [ ] You cloned or updated the repo
- [ ] You're on the `eren` branch
- [ ] You pulled the latest changes
- [ ] You can see all documentation files
- [ ] You read `START_HERE.md`
- [ ] You're ready to implement

---

## 📞 Need Help?

1. Check the error in `QUICK_REFERENCE_GUIDE.md`
2. Read the specific issue in `ADVANCED_TROUBLESHOOTING_AND_OPTIMIZATION.md`
3. Review the architecture in `ARCHITECTURE_DIAGRAM.md`
4. Follow step-by-step in `docs/STEP_BY_STEP_IMPLEMENTATION.md`

---

**Ready to start? Begin with: `cat START_HERE.md` 🚀**
