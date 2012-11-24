JAVA := java
JAVAC := javac
CLASSPATH := /usr/share/java/closure-compiler.jar:.

all: run

run: Server.class
	$(JAVA) -cp $(CLASSPATH) $(<:.class=)

%.class: %.java
	$(JAVAC) -cp $(CLASSPATH) $<
