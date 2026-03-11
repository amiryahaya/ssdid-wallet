FROM mcr.microsoft.com/dotnet/sdk:10.0-preview AS build
WORKDIR /src
COPY SsdidEmailVerify/SsdidEmailVerify.csproj SsdidEmailVerify/
RUN dotnet restore SsdidEmailVerify/SsdidEmailVerify.csproj
COPY SsdidEmailVerify/ SsdidEmailVerify/
RUN dotnet publish SsdidEmailVerify/SsdidEmailVerify.csproj -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:10.0-preview
WORKDIR /app
COPY --from=build /app .
ENV ASPNETCORE_URLS=http://+:5000
EXPOSE 5000
USER $APP_UID
ENTRYPOINT ["dotnet", "SsdidEmailVerify.dll"]
