$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $root 'extension/app/revanced/extension/youtube/vot'
$classes = Join-Path $root 'build/protocol-checks'
New-Item -ItemType Directory -Force $classes | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $source 'Proto.java') (Join-Path $source 'VotApi.java') (Join-Path $source 'PlaybackClock.java') (Join-Path $source 'AuthCallback.java') (Join-Path $PSScriptRoot 'VotTests.java') (Join-Path $PSScriptRoot 'AuthTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& java -cp $classes VotTests (Join-Path $PSScriptRoot 'upstream.properties')
if ($LASTEXITCODE -ne 0) { throw 'Protocol/clock checks failed' }
& java -cp $classes AuthTests
if ($LASTEXITCODE -ne 0) { throw 'OAuth/lively checks failed' }
$audioClasses = Join-Path $root 'build/audio-checks'
New-Item -ItemType Directory -Force $audioClasses | Out-Null
$audioSources = @(Get-ChildItem (Join-Path $PSScriptRoot 'audio') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac -encoding UTF-8 -d $audioClasses (Join-Path $source 'OriginalAudio.java') $audioSources
if ($LASTEXITCODE -ne 0) { throw 'Audio checks compilation failed' }
& java -cp $audioClasses AudioTests
if ($LASTEXITCODE -ne 0) { throw 'Audio checks failed' }
