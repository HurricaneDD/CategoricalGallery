# Categorical Gallery - 分类相册
[点击下载APK文件](hurricanedd.github.io/CategoricalGallery/app/build/outputs/apk/debug/app-debug.apk)

## 一句话介绍
与系统相册独立的一个照片管理器，支持将若干相册分配到不同工作区，便于管理照片和相册。

## 需求来源

安卓系统自带的相册APP常常只有一级的文件夹分类功能。

对于应该被归档的不常用照片，或者需要按照特定类别进行额外分类的照片（例如工作学习专区、旅行攻略专区），用户只能创建文件夹，和放置常用照片的文件夹挤在一团，大大降低了查找照片的效率。

本应用在相册的上一级，增加了一个分类层级“工作区”，工作区下一级是若干相册。另外，本应用内的相册和系统相册相互独立，在系统自带相册APP内无法看到本应用内创建和导入的相册。

## 文件管理逻辑

本软件工作目录是`/sdcard/Download/CategoricalGallery`

照片存放的目录是`/sdcard/Download/CategoricalGallery/“工作区名称”/“相册名称”/单个照片文件`。
