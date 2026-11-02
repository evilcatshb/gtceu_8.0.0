@echo off
setlocal
cd /d "%~dp0"

echo === DeepSpace GTCEu 8.0.0 GitHub sync ===

where git >nul 2>&1
if errorlevel 1 (
  echo ERROR: Git is not installed or is not on PATH.
  exit /b 1
)

if not exist ".git" (
  echo ERROR: This directory is not a Git repository.
  exit /b 1
)

REM Keep the upstream origin remote untouched; publish to the dedicated repo.
git remote get-url deepspace >nul 2>&1
if errorlevel 1 (
  git remote add deepspace https://github.com/evilcatshb/gtceu_8.0.0.git
  if errorlevel 1 exit /b 1
)

REM Ensure there is a named branch when invoked from a detached checkout.
for /f "delims=" %%B in ('git branch --show-current') do set "CURRENT_BRANCH=%%B"
if not defined CURRENT_BRANCH (
  git switch -c deepspace-gtceu-8.0.0
  if errorlevel 1 exit /b 1
)

echo.
echo Staging local source changes...
git add -A
if errorlevel 1 exit /b 1

git diff --cached --quiet
if errorlevel 1 (
  echo Creating sync commit...
  git commit -m "Sync DeepSpace local GTCEu modifications"
  if errorlevel 1 exit /b 1
) else (
  echo No new local changes to commit.
)

echo.
echo Pushing current branch to GitHub...
git push -u deepspace HEAD
if errorlevel 1 (
  echo ERROR: Push failed. Check GitHub authentication and remote status.
  exit /b 1
)

echo.
echo Sync complete: https://github.com/evilcatshb/gtceu_8.0.0
exit /b 0
