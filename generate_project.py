from pathlib import Path
import json, math, struct, wave, plistlib, re

root = Path(__file__).resolve().parent
app = root / 'ONXPhone'
plist = {
 'CFBundleDevelopmentRegion':'es', 'CFBundleDisplayName':'ONX phone',
 'CFBundleExecutable':'$(EXECUTABLE_NAME)', 'CFBundleIdentifier':'$(PRODUCT_BUNDLE_IDENTIFIER)',
 'CFBundleInfoDictionaryVersion':'6.0', 'CFBundleName':'$(PRODUCT_NAME)', 'CFBundlePackageType':'APPL',
 'CFBundleShortVersionString':'0.1.0', 'CFBundleVersion':'1', 'LSRequiresIPhoneOS':True,
 'NSMicrophoneUsageDescription':'ONX phone necesita el micrófono para tus llamadas de voz.',
 'NSCameraUsageDescription':'ONX phone necesita la cámara si usas videollamadas.',
 'UILaunchScreen':{}, 'UIUserInterfaceStyle':'Dark',
 'UISupportedInterfaceOrientations':['UIInterfaceOrientationPortrait','UIInterfaceOrientationLandscapeLeft','UIInterfaceOrientationLandscapeRight'],
}
with (app/'Info.plist').open('wb') as f: plistlib.dump(plist,f)
for name, frequencies in [('soft',(440,554)),('digital',(660,880))]:
 with wave.open(str(app/f'{name}.wav'),'wb') as w:
  w.setnchannels(1); w.setsampwidth(2); w.setframerate(22050)
  samples=[]
  for i in range(44100):
   t=i/22050; phase=t%1.0
   envelope=min(1,phase/.025,max(0,(.5-phase)/.04)) if phase<.5 else 0
   value=envelope*.16*sum(math.sin(2*math.pi*freq*t) for freq in frequencies)
   samples.append(struct.pack('<h',int(value*32767)))
  w.writeframes(b''.join(samples))
assets=app/'Assets.xcassets'; icons=assets/'AppIcon.appiconset'; icons.mkdir(parents=True,exist_ok=True)
(assets/'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}}),encoding='utf-8')
(icons/'Contents.json').write_text(json.dumps({'images':[{'filename':'Icon.png','idiom':'universal','platform':'ios','size':'1024x1024'}],'info':{'author':'xcode','version':1}}),encoding='utf-8')
files=[('App.swift','sourcecode.swift','sources'),('CallKitAdapter.swift','sourcecode.swift','sources'),('phone-bridge.js','sourcecode.javascript','resources'),('ios-transport.js','sourcecode.javascript','resources'),('brand_art.png','image.png','resources'),('soft.wav','audio.wav','resources'),('digital.wav','audio.wav','resources'),('Assets.xcassets','folder.assetcatalog','resources'),('Info.plist','text.plist.xml','none')]
def uid(i): return f'{i:024X}'
objects=[]; refs=[]; source_builds=[]; resource_builds=[]
for i,(name,kind,phase) in enumerate(files,100):
 ref=uid(i); refs.append(ref)
 objects.append(f'{ref} = {{isa = PBXFileReference; lastKnownFileType = {kind}; path = "{name}"; sourceTree = "<group>"; }};')
 if phase!='none':
  build=uid(i+100); objects.append(f'{build} = {{isa = PBXBuildFile; fileRef = {ref}; }};')
  (source_builds if phase=='sources' else resource_builds).append(build)
def ids(values): return ', '.join(values)+','
objects.extend([
f'{uid(1)} = {{isa = PBXProject; attributes = {{LastUpgradeCheck = 1600; }}; buildConfigurationList = {uid(10)}; compatibilityVersion = "Xcode 14.0"; developmentRegion = es; knownRegions = ("es", "en", "Base",); mainGroup = {uid(2)}; productRefGroup = {uid(4)}; projectDirPath = ""; projectRoot = ""; targets = ({uid(5)}); }};',
f'{uid(2)} = {{isa = PBXGroup; children = ({uid(3)}, {uid(4)}); sourceTree = "<group>"; }};',
f'{uid(3)} = {{isa = PBXGroup; children = ({ids(refs)}); path = ONXPhone; sourceTree = "<group>"; }};',
f'{uid(4)} = {{isa = PBXGroup; children = ({uid(6)}); name = Products; sourceTree = "<group>"; }};',
f'{uid(6)} = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = ONXPhone.app; sourceTree = BUILT_PRODUCTS_DIR; }};',
f'{uid(5)} = {{isa = PBXNativeTarget; buildConfigurationList = {uid(11)}; buildPhases = ({uid(9)}, {uid(7)}, {uid(8)}); buildRules = (); dependencies = (); name = ONXPhone; productName = ONXPhone; productReference = {uid(6)}; productType = "com.apple.product-type.application"; }};',
f'{uid(7)} = {{isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = ({ids(source_builds)}); runOnlyForDeploymentPostprocessing = 0; }};',
f'{uid(8)} = {{isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ({ids(resource_builds)}); runOnlyForDeploymentPostprocessing = 0; }};',
f'{uid(9)} = {{isa = PBXShellScriptBuildPhase; buildActionMask = 2147483647; files = (); inputPaths = ("$(SRCROOT)/ONXPhone/brand_art.png"); outputPaths = ("$(SRCROOT)/ONXPhone/Assets.xcassets/AppIcon.appiconset/Icon.png"); runOnlyForDeploymentPostprocessing = 0; shellPath = /bin/sh; shellScript = "/usr/bin/sips -z 1024 1024 \\\"$SRCROOT/ONXPhone/brand_art.png\\\" --out \\\"$SRCROOT/ONXPhone/Assets.xcassets/AppIcon.appiconset/Icon.png\\\" >/dev/null"; }};'.replace('\\\"','\\"'),
f'{uid(10)} = {{isa = XCConfigurationList; buildConfigurations = ({uid(12)}, {uid(13)}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};',
f'{uid(11)} = {{isa = XCConfigurationList; buildConfigurations = ({uid(14)}, {uid(15)}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; }};',
])
project_settings='CLANG_ENABLE_MODULES = YES; IPHONEOS_DEPLOYMENT_TARGET = 15.0; SDKROOT = iphoneos; SWIFT_VERSION = 5.0;'
target_settings='ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = ONXPhone/Info.plist; PRODUCT_BUNDLE_IDENTIFIER = app.roclic.onxphone; PRODUCT_NAME = "$(TARGET_NAME)"; TARGETED_DEVICE_FAMILY = 1; SUPPORTED_PLATFORMS = "iphoneos iphonesimulator"; ENABLE_USER_SCRIPT_SANDBOXING = NO; SWIFT_STRICT_CONCURRENCY = minimal;'
for n,name,settings in [(12,'Debug',project_settings),(13,'Release',project_settings),(14,'Debug',target_settings),(15,'Release',target_settings)]:
 optimization='SWIFT_OPTIMIZATION_LEVEL = "-Onone";' if name=='Debug' else 'SWIFT_OPTIMIZATION_LEVEL = "-O";'
 objects.append(f'{uid(n)} = {{isa = XCBuildConfiguration; buildSettings = {{{settings} {optimization}}}; name = {name}; }};')
project=root/'ONXPhone.xcodeproj'; project.mkdir(exist_ok=True)
project_text='// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+'\n'.join(objects)+f'\n}}; rootObject = {uid(1)}; }}\n'
project_text=re.sub(r'(?<![, (])\);',',);',project_text)
(project/'project.pbxproj').write_text(project_text,encoding='utf-8')
schemes=project/'xcshareddata/xcschemes'; schemes.mkdir(parents=True,exist_ok=True)
reference=f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{uid(5)}" BuildableName="ONXPhone.app" BlueprintName="ONXPhone" ReferencedContainer="container:ONXPhone.xcodeproj"/>'
(schemes/'ONXPhone.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{reference}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug"/>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release"/><AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>''',encoding='utf-8')
print('Generated Xcode project, shared scheme, Info.plist, icon build step and two original tones. No iOS compilation performed.')
