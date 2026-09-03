---
name: overleaf-sync
description: Sync changes between the local thesis directory and Overleaf using git subtree.
---
# Overleaf Git Subtree Syncing Skill

This skill provides instructions and scripts to sync the local `thesis/` folder (LaTeX master's thesis) with the remote Overleaf repository using git subtree.

## Remote Configuration
Check if the `overleaf` remote is configured:
```bash
git remote -v
```
If not configured, add it:
```bash
git remote add overleaf https://git@git.overleaf.com/69e3841827002aeac06eb2e8
```

## Setup Passwordless Authentication
To prevent Git from prompting for username and password during push/pull, you can use the sync script located in `scripts/sync.sh` which automatically sets up `GIT_ASKPASS` to use the token from [token.txt](file:///home/eitsei/dippa/token.txt).

## Push Local Changes to Overleaf
To push your local changes to Overleaf:
1. Ensure all local changes to `thesis/` are committed.
2. Run:
   ```bash
   git subtree push --prefix=thesis overleaf master
   ```
3. Use the token in [token.txt](file:///home/eitsei/dippa/token.txt) as the password when prompted.
   Or use the sync script:
   ```bash
   ./.agents/skills/overleaf-sync/scripts/sync.sh push
   ```

## Pull Changes from Overleaf
To pull changes from Overleaf to your local workspace:
1. Ensure all local changes are committed (very important to avoid merge conflicts).
2. Run:
   ```bash
   git subtree pull --prefix=thesis overleaf master --squash
   ```
3. Use the token in [token.txt](file:///home/eitsei/dippa/token.txt) as the password when prompted.
   Or use the sync script:
   ```bash
   ./.agents/skills/overleaf-sync/scripts/sync.sh pull
   ```

## Ground Truth & Conflict Resolution Policy
- **Ground Truth**: Overleaf is **always the ground truth** for all files under `thesis/`.
- **Conflict Handling**: Whenever merge conflicts occur during pulling from Overleaf or subtree sync, prioritize and resolve conflicts in favor of the Overleaf version.

## Troubleshooting & Conflict Resolution
If you encounter merge conflicts during `git subtree pull`:
1. Check out the Overleaf version for conflicting files under `thesis/`:
   ```bash
   git checkout --theirs <conflicted_files>
   ```
2. Mark the conflicts as resolved:
   ```bash
   git add thesis/
   git commit -m "Merge Overleaf changes into thesis (resolved conflicts in favor of Overleaf)"
   ```
3. Overleaf remains the authoritative source of truth.

