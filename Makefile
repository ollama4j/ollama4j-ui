dev:
	@echo "Setting up dev environment..."
	@command -v pre-commit >/dev/null 2>&1 || { echo "Error: pre-commit is not installed. Please install it first."; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "Error: docker is not installed. Please install it first."; exit 1; }
	pre-commit install
	pre-commit autoupdate
	pre-commit install --install-hooks

build:
	mvn -B clean install package -Dgpg.skip=true

full-build:
	mvn -B clean install package