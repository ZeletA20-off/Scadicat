[Setup]
AppName=Scadicat Launcher
AppVersion=1.0.0
AppPublisher=Scadicat
DefaultDirName=C:\scadicat
DisableDirPage=no
DefaultGroupName=Scadicat
OutputDir=installer
OutputBaseFilename=ScadicatSetup
Compression=lzma2/ultra64
SolidCompression=yes
SetupIconFile=src\main\resources\icon.ico
UninstallDisplayIcon={app}\icon.ico
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Ярлыки:"

[Files]
; Вся папка Scadicat с встроенной JRE и .exe
Source: "build\Scadicat\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Dirs]
Name: "{app}\versions"
Name: "{app}\libraries"
Name: "{app}\assets"

[Icons]
Name: "{group}\Scadicat"; Filename: "{app}\Scadicat.exe"
Name: "{group}\Удалить Scadicat"; Filename: "{uninstallexe}"
Name: "{commondesktop}\Scadicat"; Filename: "{app}\Scadicat.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Scadicat.exe"; Description: "Запустить Scadicat"; Flags: postinstall nowait skipifsilent