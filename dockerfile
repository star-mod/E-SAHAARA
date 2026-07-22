FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY ESAHAARA.java .

RUN javac ESAHAARA.java

EXPOSE 8080

CMD ["java", "ESAHAARA"]
