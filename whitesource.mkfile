SHELL := bash
config-file := wss-unified-agent.config
whitesource-jar := wss-unified-agent.jar
whitesource-jar-url := https://github.com/whitesource/unified-agent-distribution/raw/master/standAlone/${whitesource-jar}

ifndef VERSION
$(error VERSION is not defined; either define it while invoking make or use build.sh --whitesource)
endif
ifndef WHITESOURCE_API_KEY
$(error WHITESOURCE_API_KEY is not defined.)
endif

all: preamble clean whitesource

preamble:
	@echo '#############################################################'
	@echo
	@echo Starting Whitesource check.
	@echo
	@echo '#############################################################'

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
	curl -LJO ${whitesource-jar-url}
