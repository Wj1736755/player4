Feature: Capture Database Schema Definition
	As a developer
	I want to capture the current database schema structure
	So that I can save it to a file and track database schema changes over time

	Scenario: Cannot capture schema without database connection
		When I attempt to capture database schema without valid database connection
		Then the operation should be rejected

	Scenario: Cannot capture schema without specifying database
		When I attempt to capture database schema without specifying which database
		Then the operation should be rejected

	Scenario: Capture current database schema to file
		Given I have a database
		When I capture the database schema structure to a file
		Then a schema definition file should be created
		And the file should contain the database structure

	Scenario: Capture schema automatically creates output location
		Given I have a database
		When I capture the schema to a non-existent location
		Then the output location should be created automatically
		And the schema definition file should be placed there

	Scenario: Replace existing schema definition with new one
		Given I have a database
		And I already have a schema definition file
		When I capture a new schema definition at the same location
		Then the old schema definition file should be replaced
		And the new schema definition file should be valid
