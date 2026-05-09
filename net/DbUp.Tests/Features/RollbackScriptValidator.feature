Feature: Validate Rollback Scripts
	As a developer
	I want to validate that rollback scripts correctly reverse migration changes
	So that I can ensure database migrations can be safely rolled back

	Background:
		Given I have a database

	Scenario: Generate rollback scripts for multiple migrations and validate they work
		Given I have N migration scripts
		When I generate rollback scripts for each migration script
		Then I should have N rollback scripts generated
		When I validate that each rollback script correctly reverses its migration
		Then all rollback scripts should be valid
		And the database schema after each rollback should match the initial schema

	Scenario: Detect invalid rollback script
		Given I have a migration script
		And I have an invalid rollback script that does not reverse the migration
		When I validate that the rollback script correctly reverses the migration
		Then the rollback script validation should fail
		And differences between initial and rollback schema should be detected


