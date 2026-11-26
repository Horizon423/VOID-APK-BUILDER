🚀 VOID APK BUILDER

Build Android APKs from GitHub Repositories — Directly from Your Phone

Developed by Void Kernel 12

VOID APK BUILDER is a native Android application that allows you to connect your GitHub account, manage Android project files, and build APKs using GitHub Actions — all directly from your Android phone.

No computer is required for the app's normal workflow. Your GitHub repository handles the build process through GitHub Actions.

---

✨ Key Features
```
- 📦 GitHub Repository Integration — Browse, connect, and manage your repositories.
- 🛠️ Workspace Manager — Upload, create, edit, and organize project files.
- ⚡ One Click File — Import a complete project from a single ".vipnnb" package.
- 📝 Built-in Code Editor — Edit project files directly inside the application.
- 🔄 Commit & RUN ALL — Commit changes and start the APK build workflow.
- ☁️ GitHub Actions Integration — Build Android projects using GitHub's infrastructure.
- 📥 APK Output Manager — Access and download successfully generated APK artifacts.
- 🔐 Secure Token Storage — Protect your GitHub Personal Access Token using Android Keystore.
- 📋 Build Diagnostics — View build status and available error or success information.
```

📱 Main Sections

The application contains four main sections:

1. 📂 Repos

The Repos section allows you to browse repositories connected to your GitHub account.

Available functions:

- View your available GitHub repositories.
- Select a repository and open it as your workspace.
- Create a new GitHub repository using the New option.
- Access an existing repository for project development.

A connected GitHub account with the required permissions is necessary.

2. 🛠️ Workspace

Workspace is the main project management area. It allows you to add, create, edit, and organize files before building your APK.

It provides three main options:

📤 Upload

Upload files and project folders into your workspace.

Supported upload options:

- Files: Upload one or multiple files.
- Folder: Upload a folder while preserving its folder name and structure.
- Android Project: Import the contents of an Android project into the workspace root.

If an uploaded file already exists, the application can ask for confirmation before overwriting it.

Some generated or unnecessary directories are skipped during upload, including ".git", ".gradle", ".idea", build output, and "local.properties".

⚡ One Click File

One Click File is designed to import a complete project using a single ".vipnnb" package.

Instead of manually uploading multiple folders and files, you can select one supported ".vipnnb" package. The application reads the package and restores its included project folders and files into the workspace.

This makes project importing easier and faster.

Important: A ".vipnnb" package is a project-import format. It is not an APK file.

The package contains project information and file contents in a structured JSON format. It is treated as data, not as executable code.

Supported package structure:

- Package format identifier: "vipnnb"
- Package version
- Project manifest
- Folder entries
- File entries
- Relative project paths
- Supported Base64-encoded file contents

Package safety restrictions include:

- Maximum package size: 10 MB.
- Maximum entries: 5,000.
- Maximum individual file size: 5 MB.
- Absolute paths and unsafe path traversal are rejected.
- Backslash-based paths and restricted directories such as ".git" are rejected.
- ZIP containers are not accepted as ".vipnnb" packages.

These restrictions apply to ".vipnnb" importing, not to the final APK output size.

➕ Create

Use the Create option to create new files and folders directly inside your workspace.

Features:

- Create a new file.
- Create a new folder.
- Specify nested paths.
- Automatically create missing parent directories when supported.
- Avoid accidentally overwriting existing files.

For example, creating "app/src/main/java/MainActivity.kt" can create the required parent directories automatically.

📝 Built-in Code Editor

Edit your project files directly inside the application.

You can modify source code, configuration files, Gradle files, XML files, and other supported text-based project files.

💾 Commit

After editing your project, commit your pending changes to the selected GitHub repository.

Make sure your repository and GitHub token have the required permissions.

🚀 RUN ALL

RUN ALL starts the APK build process.

The application performs the required checks, commits pending changes when necessary, prepares or updates the GitHub Actions workflow, and triggers the build.

You can monitor the build progress from the application.

---

3. 📥 Output

The Output section displays APK artifacts generated after a successful build.

Available functions:

- View generated APK artifacts.
- Download the APK to your device.
- Access available build output.
- Check whether an APK was successfully generated.

APK Size Information

VOID APK BUILDER does not impose a fixed 5 MB or 10 MB limit on the final generated APK.

APK size depends on the Android project, including:

- Source code and resources.
- Libraries and dependencies.
- Images, fonts, and other assets.
- Gradle configuration.
- Build variants.
- APK optimization and packaging settings.

For example, a project can generate an APK of approximately 14 MB. The actual output size depends on the project being built.

The ".vipnnb" package restrictions described earlier do not determine the final APK size.

Important: If a build fails or no APK artifact is generated, the Output section may not contain a new APK for that build.

---

4. ⚙️ Settings

The Settings section is where you configure your GitHub connection.

🔑 Personal Access Token (PAT)

A GitHub Personal Access Token is required to connect your account and perform repository and workflow operations.

Basic connection process:
```
1. Open GitHub Settings.
2. Generate a Personal Access Token.
3. Copy the token.
4. Open VOID APK BUILDER.
5. Go to Settings.
6. Paste your token.
7. Tap Connect.
```
After successful authentication, the application can access the repositories and GitHub Actions permitted by your token.

🔐 Token Security

The application uses Android Keystore-based encryption for token storage.

- The token is protected on the device.
- The saved token is not displayed again as ordinary readable text.
- Never share your token with other people.
- Revoke tokens that you no longer use.

---

🔧 GitHub Token Permissions

For a Fine-grained Personal Access Token, configure the permissions required by your intended operations.

Recommended permissions:
```
Permission| Access
Metadata| Read
Contents| Read and Write
Actions| Read and Write
Workflows| Write
Administration| Read and Write, only if repository creation requires it
Secrets| Read, only if required by a supported operation
```
For a Classic Personal Access Token, the commonly required scopes are:

- "repo"
- "workflow"

The exact permissions required can depend on repository ownership, organization policies, and the operations you perform.

🔗 GitHub Token Links
```
- "Create Fine-grained Token" (https://github.com/settings/personal-access-tokens/new)
- "Create Classic Token" (https://github.com/settings/tokens/new)
- "Manage Your Tokens" (https://github.com/settings/tokens)
- "GitHub PAT Documentation" (https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)
```
Security Warning: Never publish your PAT in a public repository, README, source code, screenshot, or bug report.

---

⚙️ Build Process

VOID APK BUILDER uses GitHub Actions to build Android projects.

When you press RUN ALL, the application performs the following operations:

1. Validates the GitHub token.

2. Checks repository access and write permissions.

3. Checks GitHub Actions availability.

4. Checks for important Android project files, such as:
   
   - "settings.gradle" or "settings.gradle.kts"
   - "build.gradle" or "build.gradle.kts"
   - "AndroidManifest.xml"

5. Commits pending workspace changes when necessary.

6. Creates or updates the workflow file:
   
   ".github/workflows/void-apk-build.yml"

7. Dispatches the GitHub Actions workflow.

8. Checks the GitHub build status periodically, approximately every 6 seconds.

9. Downloads the generated artifact after a successful build.

10. Validates the APK and makes the artifact available in Output.

⏱️ Build Time

Build duration depends on the project, dependencies, GitHub runner availability, and caching.

Approximate expectations:

Build Stage| Estimated Time
Workflow creation or run availability| Up to around 2 minutes
First build| Around 5–15 minutes
Later builds| May be faster with caching
GitHub workflow execution limit| Depends on GitHub plan and workflow limits
Application polling limit| Up to around 120 minutes

These are estimates, not guaranteed completion times. GitHub may apply its own execution, usage, and concurrency limits.

---

📋 Build Status & Diagnostics

The application can display build statuses such as:

- ✅ Build Successful
- ❌ Build Error
- ⏳ Build Running
- 📦 APK Artifact Available

When a build fails, check the available Consoles or build diagnostics information.

Depending on the build, you may find:

- Error Console
- Success Console
- Gradle error messages
- GitHub Actions workflow logs
- Build status information

A failed build does not necessarily mean that VOID APK BUILDER is broken. The problem may be inside the Android project, Gradle configuration, dependency setup, or GitHub permissions.

Screenshots included in project documentation or promotional materials are examples of the interface and possible build results. Actual screens and messages may vary depending on the device and build.

---

🧰 Technology Stack

The project uses the following development technologies and toolchain:

Component| Version
Language| Kotlin
UI Framework| Jetpack Compose
JDK| 17
Gradle| 8.7
Android Gradle Plugin| 8.5.2
Kotlin| 1.9.24
Compose Compiler| 1.5.14
Compose BOM| 2024.06.00

The build environment is configured to use GitHub Actions for remote Android APK generation.

---

📁 Project Structure

The project follows an Android application structure.
```
VOID-APK-BUILDER/
│
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── LICENSE
├── README.md
│
├── app/
│   ├── build.gradle.kts
│   │
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           │
│           ├── assets/
│           │   └── void-apk-build.yml
│           │
│           ├── res/
│           │
│           └── java/
│               └── com/
│                   └── voidkernel/
│                       └── apkbuilder/
│                           ├── MainActivity.kt
│                           ├── AppVM.kt
│                           ├── GitHub.kt
│                           ├── Sync.kt
│                           ├── Workspace.kt
│                           ├── Vipnnb.kt
│                           ├── Secure.kt
│                           ├── Console.kt
│                           ├── Models.kt
│                           ├── Util.kt
│                           ├── Theme.kt
│                           ├── Screens1.kt
│                           ├── Workspace2.kt
│                           └── Editor.kt

```

⚠️ Known Limitations

Please keep the following limitations in mind:

- Git does not track empty directories.
- GitHub API-based file commits may not support files larger than 50 MB through the application's current commit method.
- GitHub Actions must be available and permitted for the selected repository.
- Required workflow permissions must be granted to the token.
- Some workflow permissions cannot be fully verified without attempting to create or update the workflow file.
- Android projects must contain the necessary Gradle and Android configuration files.
- Build success depends on the project's source code, dependencies, and configuration.
- GitHub may impose additional usage, runner, artifact, and workflow restrictions.

These limitations are separate from the final APK size. VOID APK BUILDER does not define a universal fixed APK output-size limit.

---

🐞 Bug Reports & Community Support

VOID APK BUILDER is an open-source project. Community participation is welcome.

We understand that no application is completely free from bugs. Since a single developer cannot identify and fix every possible issue alone, community contributions can help improve stability, reliability, and performance.

🟢 Minor Issues

For small or easily identifiable issues, users are encouraged to investigate and try basic troubleshooting independently before requesting support.

Examples:

- Minor UI problems.
- Small layout inconsistencies.
- Simple configuration mistakes.
- Incorrect file paths.
- Easily identifiable editing mistakes.
- Basic usage-related problems.

Please check the README, project documentation, and available error messages first.

🔴 Serious or Critical Issues

Contact the project maintainers or submit a detailed bug report when you encounter a serious issue that cannot reasonably be resolved independently.

Examples:

- Application crashes or fails to start.
- Critical security vulnerabilities.
- Data loss or workspace corruption.
- Serious GitHub authentication failures.
- Major repository synchronization problems.
- Build engine failures affecting normal operation.
- Widespread issues affecting multiple users.

📝 What to Include in a Bug Report

Please provide:

1. A clear description of the problem.
2. Steps to reproduce the issue.
3. Android version.
4. Device model.
5. Relevant error messages or logs.
6. Screenshots, if helpful.
7. Troubleshooting steps already attempted.

Security Reminder: Never include your GitHub Personal Access Token, password, private keys, or other confidential information in a public bug report.

🤝 Contributing

Community contributions are welcome.

You can help by:

- Reporting reproducible bugs.
- Suggesting improvements.
- Reviewing source code.
- Fixing confirmed issues.
- Improving documentation.
- Testing builds on different Android devices.

Please use the official repository's Issues and contribution facilities when available.

---

👨‍💻 Developers & Credits

Name| Role| GitHub
White Kanna| Main Developer| No GitHub account
Horizon423| Developer| "@Horizon423" (https://github.com/Horizon423)

Special Credit

Void Kernel 12 — Project Development Credit

Void Kernel 12 is the developer identity credited for the development of this project. It does not represent a separate GitHub account or an existing GitHub organization/repository.

The credit is included to acknowledge the development contribution and collaboration behind VOID APK BUILDER.

---

⚖️ License

VOID APK BUILDER is distributed under the Apache License 2.0.

This is an open-source license that permits users to use, modify, distribute, and commercially use the software, subject to the license conditions.

You must:

- Include a copy of the Apache License 2.0 when distributing the software.
- Preserve applicable copyright, patent, trademark, and attribution notices.
- Mark modified files with prominent notices indicating that changes were made.
- Preserve applicable NOTICE file contents when required by the license.

Branding & Project Identity

The Apache License 2.0 does not grant permission to use project trademarks, logos, or branding in a way that falsely suggests official endorsement or affiliation.

You may create forks and modified versions under the license terms. However:

- Do not falsely claim that your fork is the official VOID APK BUILDER release.
- Do not impersonate the original developers.
- Do not use project branding to mislead users.
- Do not remove required license or attribution notices.

Important: Apache License 2.0 allows commercial use and redistribution when its conditions are followed. The branding rules above concern misleading use of project identity; they do not cancel the rights granted by the Apache License.

See the full license text in the ""LICENSE"" **[LICENSE]( https://github.com/Horizon423/VOID-APK-BUILDER?tab=License-1-ov-)file**

Official license: https://www.apache.org/licenses/LICENSE-2.0

---

🔗 Useful Links
```
- "GitHub" (https://github.com/Horizon423)
```
```
- "Apache License 2.0" (https://www.apache.org/licenses/LICENSE-2.0
```

VOID APK BUILDER is built to make Android APK building more accessible from mobile devices.

Our goal is to simplify GitHub repository management, project editing, and APK generation through a single native Android application.

We welcome responsible contributions, constructive feedback, bug reports, and community support.

Build. Improve. Share.

🚀 VOID APK BUILDER

Developed by Void Kernel 12

Open Source • Community Driven • Powered by GitHub Actions
