# Development image: Maven, JDK 21 and the Tailwind standalone CLI.
FROM maven:3.9-eclipse-temurin-21
RUN curl -fsSL -o /usr/local/bin/tailwindcss \
       https://github.com/tailwindlabs/tailwindcss/releases/download/v4.3.3/tailwindcss-linux-x64 \
    && chmod +x /usr/local/bin/tailwindcss
ARG UID=1000
ARG GID=1000
RUN (getent group ${GID} || groupadd -g ${GID} app) && (getent passwd ${UID} || useradd -m -u ${UID} -g ${GID} app) \
    && mkdir -p /home/app/.m2 && chown -R ${UID}:${GID} /home/app
USER ${UID}:${GID}
ENV HOME=/home/app MAVEN_CONFIG=/home/app/.m2
WORKDIR /app
EXPOSE 8000
CMD ["bin/dev"]
