FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/hotel-api-*.jar app.jar
ENV JDBC_URL=jdbc:sqlite:/data/hotel.sqlite3
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://localhost:8080/api/health | grep -q ok || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
