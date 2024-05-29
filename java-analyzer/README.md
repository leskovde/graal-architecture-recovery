# JavaParser-based Repository Analyzer

Parses and resolves references in Java source code.

## Usage

1. Build the tool using Gradle:

```bash
./gradlew build
```

2. Run the tool:

```bash
./gradlew run --args='<mode> <path-to-graal-repo>'
```

where `<mode>` is one of the following:
- `c` for counting the number of classes, methods, and fields in the repository
- `project` for displaying the relationships between projects
- `class` for displaying the relationships between classes
- `package` for displaying the relationships between packages

Example: 
    
```bash
./gradlew run --args='c ../../graal'
```