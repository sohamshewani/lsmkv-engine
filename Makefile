all:
	@echo 'Building...'
	@mvn clean install
	@echo 'Built successfully.'

run-tests:
	@echo 'Running tests...'
	@mvn test
	@echo 'Tests ran successfully.'

clean:
	@echo 'Cleaning...'
	@mvn clean
	@echo 'Cleaned successfully.'