SHELL := bash
whitesource-jar := whitesource-fs-agent.jar
whitesource-jar-url := https://raw.githubusercontent.com/whitesource/fs-agent-distribution/master/standAlone/${whitesource-jar}
config-file := whitesource-fs-agent.config

ifndef VERSION
$(error VERSION is not defined; either define it while invoking make or use run.sh)
endif
ifndef WHITESOURCE_API_KEY
$(error WHITESOURCE_API_KEY is not defined; either define it while invoking make or use run.sh)
endif

all: clean whitesource

clean:

help: ${whitesource-jar}
	@java -jar ${whitesource-jar} -h

whitesource: ${config-file} ${whitesource-jar}
	@echo java -jar ${whitesource-jar} -apiKey ... -c ${config-file}
	@java -jar ${whitesource-jar} -apiKey ${WHITESOURCE_API_KEY} \
		-productVersion ${VERSION} -projectVersion ${VERSION} -c ${config-file}

${config-file}:
	@echo "ERROR: $@ doesn't exist"
	@exit 1

${whitesource-jar}:
	curl -slJO ${whitesource-jar-url}
