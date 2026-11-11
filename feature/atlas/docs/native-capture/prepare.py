#!/usr/bin/env python3
"""Prepare an isolated test project using real production atlas modules, without editing settings."""
from pathlib import Path
import argparse, hashlib, json, shlex, shutil
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--repo",type=Path,default=Path.cwd())
parser.add_argument("--dir",type=Path,required=True)
parser.add_argument("--output",type=Path,required=True)
args=parser.parse_args();repo=args.repo.resolve();out=args.dir.resolve();destination=args.output.resolve();templates=Path(__file__).parent
if not (repo/"settings.gradle.kts").is_file():raise SystemExit("--repo must name the actual repository root")
if out==repo or repo in out.parents:raise SystemExit("Choose an isolated --dir outside the source repository")
out.mkdir(parents=True,exist_ok=True);destination.mkdir(parents=True,exist_ok=True)
for name in ["build.gradle.kts","capture.init.gradle"]:
 source=templates/name;target=out/name
 if source.resolve()!=target.resolve():shutil.copyfile(source,target)
manifest=out/"src/main/AndroidManifest.xml";manifest.parent.mkdir(parents=True,exist_ok=True)
manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" />\n')
items=[]
for relative in ["src/test/kotlin/app/familyjourney/screens/ShotKit.kt","src/test/kotlin/app/familyjourney/screens/AtlasShots.kt","src/main/res/drawable-nodpi/ggf_atlas_test_lm001.webp"]:
 source=repo/"screens"/relative;target=out/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
 actual=hashlib.sha256(target.read_bytes()).hexdigest();assert actual==hashlib.sha256(source.read_bytes()).hexdigest()
 items.append({"repository_source":"screens/"+relative,"sha256":actual,"bytes_unchanged":True})
(out/"capture-provenance.json").write_text(json.dumps({"production_modules":[":feature:atlas",":core:atlas",":core:ui"],"copied_test_sources":items,"fake_SDK_or_production_classes":False,"production_settings_modified":False},indent=2)+"\n")
cmd=[str(repo/"gradlew"),"-I",str(out/"capture.init.gradle"),"-Datlas.capture.dir="+str(out),"-Patlas.capture.output="+str(destination),":atlasCapture:testDebugUnitTest","--tests","app.familyjourney.screens.AtlasShots","--no-daemon","--console=plain"]
print("Prepared isolated actual Compose capture; run with a real JDK17 and SDK35:")
print(shlex.join(cmd))
