# how-sb-devtools-works

spring-boot-devtools 通过自定义类加载器机制破坏了 Java 的双亲委派模型，以实现热部署和快速重启的功能。

> springboot-devtools 是如何工作的？

## 前期准备

搭建一个 SpringBoot 项目，并加上 DevTools 依赖。

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
  </dependency>
</dependencies>
```

写一个启动类

```java
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        System.out.println("Main.class ClassLoader= " + Main.class.getClassLoader());
        SpringApplication.run(Main.class, args);
    }
}
```

启动的时候看一下是由哪个 ClassLoader 来进行启动类加载的。

## 应用启动

> SpringBoot 启动！

观察输出

```shell
Main.class ClassLoader= jdk.internal.loader.ClassLoaders$AppClassLoader@2b193f2d
Main.class ClassLoader= org.springframework.boot.devtools.restart.classloader.RestartClassLoader@279ec7d6

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)
```

> Wow 小 DevTools 子，没想到这么快就出现了。

开始的时候是正常的 AppClassLoader 来进行 Main.class 的加载，接着又换成 RestartClassLoader 来加载 Main.class，并继续执行应用程序启动流程。而 RestartClassLoader 就是 DevTools 自定义的类加载器。

spring-boot-devtools 为了支持热部署（即在应用运行时动态替换类定义而无需重启整个应用），采用了不同的策略。它使用了一个特殊的类加载器 RestartClassLoader，该类加载器不会遵循传统的双亲委派模型。

具体来说，RestartClassLoader 在查找类时首先会尝试自己加载类，而不是先委托给父类加载器。这种方式打破了传统的双亲委派机制，因为它允许子类加载器优先于父类加载器加载类。

正不正确不要紧，大胆猜想：

## 猜想一

> 引入 DevTools 依赖之后：从应用启动开始，到整个生命周期结束，SpringBoot 应用的类用的都是 RestartClassLoader 来进行类加载的。

接下来，让我们来进行验证。

首先自定义一个 Controller 和一个 Configuration 类。

```java
DevController
DevConfig
```

接着看一下启动的时候使用的是什么类加载器来加载**自定义类**和 SpringBoot **内部类**。稍微修改一下 main 方法

```java
@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        System.out.println("Main.class ClassLoader= " + Main.class.getClassLoader());
        SpringApplication.run(Main.class, args);

        System.out.println("+++++ springboot class +++++");
        System.out.println("RestartClassLoader.class ClassLoader= " + RestartClassLoader.class.getClassLoader());
        System.out.println("SpringApplication.class ClassLoader= " + SpringApplication.class.getClassLoader());
        System.out.println("ApplicationContext.class ClassLoader= " + ApplicationContext.class.getClassLoader());
        System.out.println("BeanFactory.class ClassLoader= " + BeanFactory.class.getClassLoader());

        System.out.println("+++++ custom class +++++");
        System.out.println("DevController.class ClassLoader= " + DevController.class.getClassLoader());
        System.out.println("DevConfig.class ClassLoader= " + DevConfig.class.getClassLoader());
    }
}
```

查看输出

```shell
+++++ springboot class +++++
RestartClassLoader.class ClassLoader= jdk.internal.loader.ClassLoaders$AppClassLoader@2b193f2d
SpringApplication.class ClassLoader= jdk.internal.loader.ClassLoaders$AppClassLoader@2b193f2d
ApplicationContext.class ClassLoader= jdk.internal.loader.ClassLoaders$AppClassLoader@2b193f2d
BeanFactory.class ClassLoader= jdk.internal.loader.ClassLoaders$AppClassLoader@2b193f2d
+++++ custom class +++++
DevController.class ClassLoader= org.springframework.boot.devtools.restart.classloader.RestartClassLoader@7a44f6df
DevConfig.class ClassLoader= org.springframework.boot.devtools.restart.classloader.RestartClassLoader@7a44f6df
```

…

**结论**

SpringBoot 内部类使用 AppClassLoader 来加载；自定义类使用  RestartClassLoader 来加载。

> 为什么呢？
>
> 其实逻辑也简单，我们编写应用的时候一般都是修改自定义的类，Devtools 只需要加载修改的部分就可以了，使用 RestartClassLoader 来进行加载方便热部署。

…

---

## DevTools 启动时机

位置：

* RestartApplicationListener#onApplicationStartingEvent

…

启动流程如下：
```shell
RestartApplicationListener#onApplicationStartingEvent
|-  Restarter#initialize –> immediateRestart –> doStart –> relaunch
|-  Restarter#restart 重新执行 main 方法
```

…

```java
private void onApplicationStartingEvent(ApplicationStartingEvent event) {
  // It's too early to use the Spring environment, but we should still allow users to disable restart using a System property.
  String enabled = System.getProperty(ENABLED_PROPERTY);
  RestartInitializer restartInitializer = null;
  // Restart disabled due to context in which it is running
  // ...
  // Restart enabled irrespective of application packaging due to System property '%s' being set to true
  // ...
  // Restart disabled due to an agent-based reloader being active
  // ...
  // 初始化 Restarter
  Restarter.initialize(args, false, restartInitializer, restartOnInitialize);
  // Restart disabled due to System property '%s' being set to false
  // ...
}
```

DevTools 会监听应用启动事件，在应用启动的时候初始化一个 `org.springframework.boot.devtools.restart.Restarter` 用来专门启动/重启应用。

我们之前看到的 RestartClassLoader 就在 Restarter#doStart 中

```java
private Throwable doStart() throws Exception {
  URL[] urls = this.urls.toArray(new URL[0]);
  // 保存本轮启动修改了的文件
  ClassLoaderFiles updatedFiles = new ClassLoaderFiles(this.classLoaderFiles);
  ClassLoader classLoader = new RestartClassLoader(this.applicationClassLoader, urls, updatedFiles);
  return relaunch(classLoader); // 收集到更新后的文件，使用 RestartClassLoader 来启动应用
}
```

接着重启应用 Restarter#relaunch

```java
class RestartLauncher extends Thread {} // RestartLauncher 本质上是一个线程类

protected Throwable relaunch(ClassLoader classLoader) throws Exception {
  RestartLauncher launcher = new RestartLauncher(classLoader, this.mainClassName, this.args, this.exceptionHandler);
  launcher.start(); // 创建一个新的 RestartLauncher 线程并 start，开一个新的线程重新执行 main 方法
  launcher.join();
  return launcher.getError();
}
```

开一个新的线程重新执行 main 方法

```java
public void run() { // RestartLauncher#run
  try {
    Class<?> mainClass = Class.forName(this.mainClassName, false, getContextClassLoader());
    Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);
    mainMethod.setAccessible(true);
    mainMethod.invoke(null, new Object[] { this.args });
  } catch (Exception ex) {}
}
```

这样一来逻辑就很清晰了。

…

> DevTools 会监听 ApplicationStartingEvent，在 Spring 应用启动的时候开启一个新的线程，利用 RestartClassLoader 重新加载并再执行一遍 main 方法。每次重启时，RestartClassLoader 都会被重新创建，这有助于确保新的类定义能够被正确加载，同时也避免了旧的类定义对新版本的影响
。

…

按照这个逻辑，SpringApplication#run 会执行两次。

> 打断点观察可以看到确实如此。

…

在第一次启动 Spring 应用的时候已经使用 AppClassLoader 将 SpringBoot 内部类加载过一遍了，进行热加载的时候再次使用 RestartClassLoader 来重启应用的时候就不需要再使用 RestartClassLoader  来加载已经加载过的类了。

…

> 在项目中遇到过一个和 DevTools 相关的 “Bug”，情景如下：
>
> 项目依赖了 DevTools， 项目中有使用到自定义的类加载器，暂且称为 CustomClassLoader。
>
> 项目结构如下：
>
> ```
> project-a
> |-- base
> |-- core 依赖 base，依赖 dev-tools，包含 Spring 主启动类，有 CustomClassLoader。
> |-- implement 依赖 base
> ```
>
> base 中有一个接口 CustomInterface，implement 中有一个实现类 CustomImpl。然后将 implement 打包成 jar，再通过 CustomClassLoader 来动态加载。
>
> 在 CustomImpl 中已经明确实现了 CustomInterface，而且实现类已经通过 SPI 接口暴露。但是在使用 CustomClassLoader 来动态加载 CustomImpl 的时候提示：`CustomInterface: CustomImpl  not a subtype`。实在是奇怪。
>
> 其实问题就出现在 DevTools 依赖上。因为使用了 DevTools，所以 CustomInterface 现在的 ClassLoader 已经变成了 RestartClassLoader
>
> ```
>               ┌────────────────┐
>               │ URLClassLoader │
>               └───┬─────────┬──┘
>                   │         │
>    ┌──────────────┴───┐   ┌─┴────────────┐
>    │RestartClassLoader│   │CusClassLoader│
>    ├──────────────────┤   ├──────────────┤
>    ├──────────────────┤   ├──────────────┤
>    │CusInterface      │   │CusImplement  │
>    └──────────────────┘   └──────────────┘
> ```
>
> （ASCII 图由 asciiflow.com 生成）
>
> 当我们再使用 CustomClassLoader 想要去加载 CustomerInterface 的时候会将请求传递给父类加载器，进行双亲委派加载。
>
> 在我们的例子中，因为 CustomerInterface 是由 RestartClassLoader 来加载的，CustomClassLoader 就算利用双亲委派机制也无法访问到 CustomerInterface。所以在加载的时候报错 `xxx is not a subtype`。
>
> 要解决问题很简单，只要将 dev-tools 的依赖去掉即可。
>
> …

…

---

<br>

## DevTools 如何自动重启

这和一个叫做 LiveReloadServer 的本地服务有关。

**何时启动？**

看 `LocalDevToolsAutoConfiguration.LiveReloadConfiguration`

```java
@Bean
OptionalLiveReloadServer optionalLiveReloadServer(LiveReloadServer liveReloadServer) {
  return new OptionalLiveReloadServer(liveReloadServer);
}
```

这里注入了一个 OptionalLiveReloadServer，OptionalLiveReloadServer 配置完成之后会在

* `OptionalLiveReloadServer#afterPropertiesSet`

方法中启动 LiveReloadServer。

…

**何时重启？**

LiveReloadServer 启动之后，会收集在它运行期间所有被修改过的文件，然后通过 Request 请求将修改过的文件发送到 

RestartServer，由 RestartServer 负责重启应用。

```java
// HttpRestartServer
public void handle(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
  try {
    // ...
    ObjectInputStream objectInputStream = new ObjectInputStream(request.getBody());
    // 将所有修改过的文件通过 Request 发送
    ClassLoaderFiles files = (ClassLoaderFiles) objectInputStream.readObject();
    objectInputStream.close();
    this.server.updateAndRestart(files);
    response.setStatusCode(HttpStatus.OK);
  } catch (Exception e) {}
  // ...
}
```

 RestartServer 更新修改并重启

```java
public void updateAndRestart(ClassLoaderFiles files) {
  Set<URL> urls = new LinkedHashSet<>();
  Set<URL> classLoaderUrls = getClassLoaderUrls();
  for (SourceDirectory directory : files.getSourceDirectories()) {
    for (Entry<String, ClassLoaderFile> entry : directory.getFilesEntrySet()) {
      for (URL url : classLoaderUrls) {
        if (updateFileSystem(url, entry.getKey(), entry.getValue())) {
          urls.add(url); // 由 RestartClassLoader 进行加载
        }
      }
    }
    urls.addAll(getMatchingUrls(classLoaderUrls, directory.getName()));
  }
  updateTimeStamp(urls);
  restart(urls, files);
}
```

…

以上就是 DevTools 由启动到自动重启的全过程。

…

---

## 帮助重启主启动类的线程

在重启主启动类的时候需要一个线程 RestartLauncher

```java
protected Throwable relaunch(ClassLoader classLoader) throws Exception {
    RestartLauncher launcher = new RestartLauncher(classLoader, this.mainClassName, this.args, this.exceptionHandler);
    launcher.start();
    launcher.join(); // Wait for the launcher to finish
    return launcher.getError();
}
```

…

---

<br>

## 参考

- spring-boot-devtools-3.2.0 源码
