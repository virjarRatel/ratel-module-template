# ratel-module-template

## 使用方法
ratel项目的模版，你可以基于本项目快速构建一个适合ratel框架的插件项目。

使用方法为执行脚本:``./template.sh params``

如：
```
virjar-share:ratel-demo virjar$ ./template.sh -p com.tencent.mm -m crack-wechat 

> Task :createhelper:compileJava
警告: [options] 未与 -source 1.7 一起设置引导类路径
1 个警告

BUILD SUCCESSFUL in 0s
2 actionable tasks: 2 executed
param: -p com.tencent.mm -m crack-wechat
virjar-share:ratel-demo virjar$ 

```

执行完即可看到在本项目生成的子模块:
![template-demo.png](template-demo.png)


你还可以通过一个apk构建破解模版,如：
```
virjar-share:ratel-demo virjar$ ./template.sh -a ~/Downloads/com.youdao.dict_7.8.7_7080700.apk 
Starting a Gradle Daemon, 1 incompatible Daemon could not be reused, use --status for details

> Configure project :crack-demoapp
> Task :createhelper:compileJava
警告: [options] 未与 -source 1.7 一起设置引导类路径
1 个警告

BUILD SUCCESSFUL in 7s
2 actionable tasks: 2 executed
param: -a /Users/virjar/Downloads/com.youdao.dict_7.8.7_7080700.apk
virjar-share:ratel-demo virjar$ 

```