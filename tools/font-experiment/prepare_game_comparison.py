"""Prepare isolated playable Android/Tekton experiments without editing the player."""
import argparse
import json
import shutil
from pathlib import Path


def prepare(destination, fonts):
    root = Path(__file__).resolve().parents[2]
    destination, fonts = Path(destination).resolve(), Path(fonts).resolve()
    if destination.exists():
        raise ValueError('output directory already exists')
    required = ['tekton-recovered.otf','tekton-italic-recovered.otf']
    if not all((fonts/name).is_file() for name in required):
        raise ValueError('both recovered private fonts are required')
    for mode in ['android','tekton']:
        target = destination / mode
        shutil.copytree(root/'android', target, ignore=shutil.ignore_patterns('build','.gradle','.kotlin','local.properties'))
        app = target/'app'
        build = app/'build.gradle.kts'
        source = build.read_text(encoding='utf-8')
        source = source.replace('applicationId = "org.rigorcore.caserecomp.synthetic"', f'applicationId = "org.rigorcore.caserecomp.huntsville.{mode}fonts"')
        source = source.replace('versionName = "0.6.0"', 'versionName = "0.6.0-font-experiment"')
        build.write_text(source,encoding='utf-8')
        manifest = app/'src/main/AndroidManifest.xml'
        source = manifest.read_text(encoding='utf-8').replace('android:label="Case Recomp"', f'android:label="Huntsville - {mode.title()} experimental"')
        source = source.replace('android:name=".DirectorLauncherActivity"\n            android:exported="false"', 'android:name=".DirectorLauncherActivity"\n            android:exported="true"')
        manifest.write_text(source,encoding='utf-8')
        debug_manifest = app/'src/debug/AndroidManifest.xml'
        if debug_manifest.exists():
            source = debug_manifest.read_text(encoding='utf-8').replace('android:label="Case Recomp Debug"', f'android:label="Huntsville - {mode.title()} experimental"')
            debug_manifest.write_text(source,encoding='utf-8')
        if mode == 'tekton':
            kotlin = app/'src/main/kotlin/org/rigorcore/caserecomp/app'
            text = kotlin/'AndroidDirectorPorts.kt'
            source = text.read_text(encoding='utf-8')
            anchor = '    private val presentation: DirectorTextPresentation = DirectorTextPresentation(),\n'
            if source.count(anchor) != 1:
                raise ValueError('unexpected text renderer structure')
            source = source.replace(anchor, anchor+'    context: Context,\n',1)
            anchor = '    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)\n'
            source = source.replace(anchor, anchor+'''    private val recoveredRegular = Typeface.createFromAsset(context.assets, "tekton-recovered.otf")
    private val recoveredItalic = Typeface.createFromAsset(context.assets, "tekton-italic-recovered.otf")
    private fun recovered(face: String) = "tekto" in face.lowercase()
    private fun font(face: String): FontPresentation = if (recovered(face))
        FontPresentation(linePitchScale = presentation.font(face).linePitchScale)
    else presentation.font(face)
''',1)
            source = source.replace('presentation.font(member.font)', 'font(member.font)')
            source = source.replace('paint.typeface = Typeface.create(family(member.font), style)', '''paint.typeface = if (recovered(member.font)) {
            if (italic) recoveredItalic else recoveredRegular
        } else Typeface.create(family(member.font), style)''')
            text.write_text(source,encoding='utf-8')
            launcher=kotlin/'DirectorLauncherActivity.kt'
            source=launcher.read_text(encoding='utf-8').replace('AndroidDirectorText(profile.text)', 'AndroidDirectorText(profile.text, this)')
            launcher.write_text(source,encoding='utf-8')
            assets=app/'src/main/assets';assets.mkdir(exist_ok=True)
            for name in required:shutil.copyfile(fonts/name,assets/name)
    (destination/'experiment.json').write_text(json.dumps({'kind':'isolated playable font experiment','source_player_changed':False,'packages':['org.rigorcore.caserecomp.huntsville.androidfonts.debug','org.rigorcore.caserecomp.huntsville.tektonfonts.debug'],'tekton_size_width_scales':'nominal 1.0; existing box fitting and line pitch retained'},indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output',required=True);p.add_argument('--fonts',required=True)
    a=p.parse_args();prepare(a.output,a.fonts)
