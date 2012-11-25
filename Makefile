JAVA := java
JAVAC := javac
CLASSPATH := /usr/share/java/closure-compiler.jar:.

all: run

run: Server.class
	$(JAVA) -cp $(CLASSPATH) $(<:.class=)

%.class: %.java
	$(JAVAC) -cp $(CLASSPATH) $<

test:
	curl http://localhost:8888 --data-ascii 'js_code=a=function(){var z   =alert(13-1);;}&compilation_level=WHITESPACE_ONLY&output_format=text&output_info=errors'
