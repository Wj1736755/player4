# DbUp Tests

Unit tests for the CustomJournal implementation using LocalDB.

## Prerequisites

- LocalDB (usually installed with Visual Studio or SQL Server Express)
- .NET SDK 10.0

## Running Tests

From the solution root:
```
dotnet test
```

From the test project directory:
```
cd DbUp.Tests
dotnet test
```

## Test Coverage

- Table creation with all columns
- Temporal table creation  
- Trigger creation and functionality
- Script storage with rollback scripts
- AppliedBy/ModifiedBy audit columns
- Temporal history tracking
- GetExecutedScripts functionality

Each test creates a unique LocalDB database that is automatically cleaned up after the test runs.

