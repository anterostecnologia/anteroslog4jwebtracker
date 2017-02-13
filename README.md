# Anteros Log4j Web Tracker

O **Anteros Log4j Web Tracker** é uma ferramenta web _open source_ baseada no [Log4j Web Tracker](https://github.com/mrsarm/log4jwebtracker) para configurar em tempo real os logs do [Apache Log4j](http://logging.apache.org/log4j/) da aplicação. Ele também possui uma aba para ler os logs em tempo real, ou fazer download dos mesmos.

## Introdução

Na primeira aba (_**Configuração**_) você pode alterar os níveis de log da aplicação, incluindo o log do nível `root`. As alterações são aplicadas no momento, mas não alteram as configurações originais do arquivo `log4j.properties`, `log4j.xml` ou qualquer outro arquivo de configuração. Se você reinicar a aplicação, as configurações originais serão aplicadas novamente.

Na segunda aba (_**Log**_), você pode selecionar o arquivo do log, configurar a quantidade de linhas, selecionar se quer atualizar o log automaticamente após *5 segundos* e visualizar o log ou fazer download do mesmo.


## Configuração do Anteros Log4j Web Tracker

### Configuração do Maven

Se você estiver usando o **Apache Maven**, adicione esse artefato no `pom.xml`:

```xml
    <dependency>
        <groupId>br.com.anteros</groupId>
        <artifactId>Anteros-Log4j-Web-Tracker</artifactId>
        <version>1.0.0</version>
    </dependency>
```

### Configuração do Apache Log4j

O **Apache Log4j** não precisa de uma configuração especial para ser usado na ferramenta, só é necessário configurá-lo normalmente. Muitos desenvolvedores usam a classe utilitária do **Spring** no contexto web (`org.springframework.web.util.Log4jConfigListener`).

Você também pode utilizar a configuração automática (o arquivo de configuração deve ser colocado na pasta `WEB-INF/classes`), ou a configuração manual, invocando `org.apache.log4j.PropertyConfigurator.configure(String configFilename)`.

O **Anteros Log4j Web Tracker** fornece uma classe de servlet para fazer isso, mas é opcional:

```xml
    <servlet>
            <servlet-name>Log4jInitServlet</servlet-name>
            <servlet-class>br.com.anteros.log4jwebtracker.servlet.init.Log4jInitServlet</servlet-class>
            <init-param>
                    <param-name>log4jConfigLocation</param-name>
                    <param-value>/WEB-INF/log4j.properties</param-value>
            </init-param>
            <load-on-startup>1</load-on-startup>
    </servlet>
```

### Configuração do Web Tracker

Para configurar o Web Tracker, no arquivo `WEB-INF/web.xml` é necessário adicionar o mapeamento:

```xml
    <servlet>
        <servlet-name>TrackerServlet</servlet-name>
        <servlet-class>br.com.anteros.log4jwebtracker.servlet.TrackerServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>TrackerServlet</servlet-name>
        <url-pattern>/tracker/*</url-pattern>
    </servlet-mapping>
```

No exemplo, a ferramenta foi mapeada como `/tracker/*`, portanto, se o aplicativo estiver acessível em [http://localhost:8080/myapp](http://localhost:8080/myapp), a URL para acessar o tracker é:

[http://localhost:8080/myapp/tracker](http://localhost:8080/myapp/tracker)


## Licença ##

Apache 2.0

http://www.apache.org/licenses/LICENSE-2.0


<center>
![alt text](https://avatars0.githubusercontent.com/u/16067889?v=3&u=ab2eb482a16fd90a17d7ce711885f0bdc0640997&s=64)  
Anteros Tecnologia
