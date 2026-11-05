SOURCES := $(shell find src -name '*.java' -print)
JAVA_FLAGS := -Xmx160m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8

.PHONY: compile test demo
compile:
	@mkdir -p build/classes
	javac --release 21 -encoding UTF-8 -Xlint:all -d build/classes $(SOURCES)

test: compile
	java $(JAVA_FLAGS) -cp build/classes br.com.lofrano.tlslab.LabMain test

demo: compile
	java $(JAVA_FLAGS) -cp build/classes br.com.lofrano.tlslab.LabMain demo
