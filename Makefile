MODULE_DIR := ether-vault
MAVEN ?= mvn

.PHONY: test package run

test:
	cd $(MODULE_DIR) && $(MAVEN) test

package:
	cd $(MODULE_DIR) && $(MAVEN) package

run:
	cd $(MODULE_DIR) && $(MAVEN) package -DskipTests && java -jar target/ether-vault.jar
