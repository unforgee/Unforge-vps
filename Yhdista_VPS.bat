@echo off
rem ------------------------------------------------------------
rem Yhdista_VPS.bat - Deploy repository to Linux VPS
rem ------------------------------------------------------------

rem ==== Määrittele muuttujat ==== 
set REPO_URL=https://github.com/smoukkiii/Unforge-ai.git
rem Paikallinen työskentelykansio (muokkaa tarvittaessa)
set LOCAL_DIR=%~dp0

rem ==== SSH-yhteysasetukset ==== 
rem Vaihda nämä omiin asetuksiisi
set VPS_USER=your_vps_user
set VPS_HOST=your.vps.host
rem Käytetään eri porttia kuin oletus (22). Määritä portti, esim. 2222
set VPS_SSH_PORT=2222
rem Polku VPS:n kotikansiossa, jossa projektia ajetaan
set VPS_TARGET_DIR=~/unforge_ai

rem ==== GitHub – pakkaus ja push ==== 
pushd "%LOCAL_DIR%"
rem Varmista, että kaikki muutokset on lisätty
git add .
rem Kirjoita commit viesti (muokkaa halutessasi)
git commit -m "Deploy to VPS"
rem Push kaikki muutokset GitHubiin
git push origin main
popd

rem ==== Pakataan tiedostot deployment-arkistoon (tar.gz) ==== 
set ARCHIVE_NAME=unforge_ai_deploy.tar.gz
pushd "%LOCAL_DIR%"
rem Poistetaan mahdolliset git‑metadata jos et halua ne VPS:lle
rem Jos haluat sisällyttää .git kansio, poista seuraava rivi
if exist .git rmdir /s /q .git
rem Luo arkisto
tar -czf "%ARCHIVE_NAME%" *
popd

rem ==== Kopioi arkisto VPS:lle scp:llä ==== 
scp -P %VPS_SSH_PORT -o StrictHostKeyChecking=no "%LOCAL_DIR%%ARCHIVE_NAME%" %VPS_USER%@%VPS_HOST%:~/

rem ==== Yhdistä VPS:ään ja pura arkisto ==== 
ssh -p %VPS_SSH_PORT %VPS_USER%@%VPS_HOST% "mkdir -p %VPS_TARGET_DIR% && tar -xzf ~/%ARCHIVE_NAME% -C %VPS_TARGET_DIR% && rm ~/%ARCHIVE_NAME%"

rem ==== (Valinnainen) Asenna riippuvuudet VPS:llä ==== 
ssh -p %VPS_SSH_PORT %VPS_USER%@%VPS_HOST% "cd %VPS_TARGET_DIR% && sudo apt-get update && sudo apt-get install -y python3-pip && pip3 install -r requirements.txt"

rem ==== Valmis ==== 
echo Deployment completed.
pause
