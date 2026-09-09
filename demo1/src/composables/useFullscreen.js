export const useFullscreen =()=>{ 
    const isFullscreen =()=>{//检查当前页面是否处于全屏状态
    //兼容性处理：不同浏览器对该属性命名不同
    return !!document.fullscreenElement//CHrome/Firefox等现代浏览器
     || !!document.webkitFullscreenElement//Safari/旧版Chrome
     || !!document.mozFullScreenElement //firefox旧版，注意Screen大写
     || !!document.msFullscreenElement//IE/Edge旧版
     //返回值为true（全屏状态）或 false（非全屏状态），!! 用于将值转换为布尔类型。
    
}
const enterFullscreen = (element = document.documentElement) => {//让指定元素进入全屏模式，element是要全屏的元素，默认值为document.documentElement（即整个页面）。
//调用元素的“请求全屏”方法触发全屏
  if (element.requestFullscreen) {
    element.requestFullscreen();
  } else if (element.webkitRequestFullscreen) {
    element.webkitRequestFullscreen();
  } else if (element.mozRequestFullScreen) {
    element.mozRequestFullScreen();//(注意Screen大写)
  } else if (element.msRequestFullscreen) {
    element.msRequestFullscreen();
  }
};
const exitFullscreen = () => {//退出当前全屏模式，回到正常页面状态
    //调用document的“退出全屏”方法触发退出
  if (document.exitFullscreen) {
    document.exitFullscreen();
  } else if (document.webkitExitFullscreen) {
    document.webkitExitFullscreen();
  } else if (document.mozCancelFullScreen) {
    document.mozCancelFullScreen();//注意方法名为“取消全屏”
  } else if (document.msExitFullscreen) {
    document.msExitFullscreen();
  }
};
const toggleFullscreen = (element) => {//根据当前状态切换全屏
  isFullscreen() ? exitFullscreen() : enterFullscreen(element);
};
return{enterFullscreen,exitFullscreen,toggleFullscreen,isFullscreen};
}