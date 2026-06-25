# Contributing to Shellom

Welcome to Shellom! This page will help you set up and contribute.

## Building

The project uses [Mise](https://mise.jdx.dev/) and Gradle for building.

### Setup

1. **Install Mise**: Follow the instructions on the [Mise website](https://mise.jdx.dev/).
2. **Install Tools**: Run `mise install`.
   * **Smart SDK Management**: Mise will check for a system-installed Android SDK first. If found, it uses it. If not, it will automatically install a managed version for you.
3. **Build**: Run `gradle assembleDebug` to build the project.

## Lint & Format

### Run Lint
To check for linting issues, run:
```bash
gradle ktlintCheck
```

### Run Format
To automatically fix most linting issues, run:
```bash
gradle ktlintFormat
```
