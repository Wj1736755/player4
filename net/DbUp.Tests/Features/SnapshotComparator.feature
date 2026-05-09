Feature: Compare Database Schema Versions
	As a developer
	I want to compare two versions of database schema
	So that I can see what changed and generate migration script

	Scenario: Verify two database schemas are the same
		Given I have captured schema from the same database twice
		When I compare these two schema definitions
		Then they should match with no differences

	Scenario: Identify differences between database schemas
		Given I have captured schema from database A
		And I have captured schema from database B with different structure
		When I compare these two schema definitions
		Then differences between them should be detected

	Scenario: Generate migration script for identical schemas
		Given I have captured schema from the same database twice
		When I generate SQL migration script between these schema definitions
		Then the script should be empty

	Scenario: Generate migration script showing schema changes
		Given I have captured schema from database A
		And I have captured schema from database B with different structure
		When I generate SQL migration script between these schema definitions
		Then the script should contain SQL commands to apply the differences

	Scenario: Generate migration script automatically creates output location
		Given I have captured schema from database A
		And I have captured schema from database B
		When I generate SQL migration script to a non-existent location
		Then the output location should be created automatically
		And the migration script file should be placed there

	Scenario: Cannot compare schemas when definition is missing
		Given one of the database schema definition files does not exist
		When I attempt to compare the schema definitions
		Then the operation should be rejected
