package com.majian.mvcframework.servlet;

import com.majian.mvcframework.annotation.MjAutowired;
import com.majian.mvcframework.annotation.MjController;
import com.majian.mvcframework.annotation.MjRequestMapping;
import com.majian.mvcframework.annotation.MjService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MjDispatcherServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    // 和web.xml中的param—name内容一致
    private static final String LOCATION = "contextConfigLocation";
    // 保存所有配置信息
    private Properties properties = new Properties();
    // 保存所有扫描到的相关的类名
    private List<String> classNames = new ArrayList<String>(512);
    // IOC容器
    private Map<String, Object> ioc = new HashMap<String, Object>(512);
    // 保存所有Url和方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap<>(512);

    public MjDispatcherServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        if (handlerMapping.isEmpty()) {
            return;
        }
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        uri = "/" + uri.replace(contextPath, "").replaceAll("/+", "");
        if (!handlerMapping.containsKey(uri)) {
            try {
                resp.getWriter().write("404 not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Method method = handlerMapping.get(uri);
        // 获取方法参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 获取请求参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 参数值保存
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            // 根据参数名称
            Class parameter = parameterTypes[i];
            if (parameter == HttpServletRequest.class) {
                paramValues[i] = req;
                continue;
            } else if (parameter == HttpServletResponse.class) {
                paramValues[i] = resp;
                continue;
            } else if (parameter == String.class) {
                // 遍历参数
                for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                    String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            try {
                method.invoke(ioc.get(beanName), paramValues);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * 初始化配置文件
     * @return void
     * @author majian
     * @creed: Talk is cheap,show me the code
     * @date 2018/11/11 16:11
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init();
        // 1. 加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        // 2. 扫描所有相关的类
        doScanner(properties.getProperty("scanPackage"));
        // 3. 初始化所有相关类的实例，将其放入IOC容器中
        doInstance();
        // 4. 依赖注入
        doAutowired();
        // 5. 构造方法和Url映射（HandlerMapping）
        initHandlerMapping();
        // 6. 提示信息
        System.out.println("majian mvcframework is init");
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        ioc.forEach((k, v) -> {
            Class<?> clazz = v.getClass();
            if (!clazz.isAnnotationPresent(MjController.class)) {
                // java8中foreach中无法使用break/continue，使用return并不会返回，而是进行下一个遍历
                return;
            }
            // 类名上是否使用了MjRequestMapping
            String baseUrl = "";
            if (clazz.isAnnotationPresent(MjRequestMapping.class)) {
                MjRequestMapping mjRequestMapping = clazz.getAnnotation(MjRequestMapping.class);
                baseUrl = mjRequestMapping.value();
            }
            // 方法上使用的MjRequestMapping
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(MjRequestMapping.class)) {
                    continue;
                }
                MjRequestMapping annotation = method.getAnnotation(MjRequestMapping.class);
                String url = ("/" + baseUrl + "/" + annotation.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("路由映射配置完成...");
            }

        });
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        ioc.forEach((k, v) -> {
            // 拿到当前遍历的类的所有字段
            Field[] fields = v.getClass().getDeclaredFields();
            for (Field field : fields) {
                // 私有属性允许访问
                field.setAccessible(true);
                if (!field.isAnnotationPresent(MjAutowired.class)) {
                    continue;
                }
                MjAutowired mjAutowired = field.getAnnotation(MjAutowired.class);
                String beanName = mjAutowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getSimpleName();
                }
                try {
                    System.out.println("开始注入...");
                    field.set(v, ioc.get(beanName));
                    System.out.println("注入了"+field.getName());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        });
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MjController.class)) {
                    // 首字母小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(MjService.class)) {
                    MjService mjService = clazz.getAnnotation(MjService.class);
                    String beanName = mjService.value();
                    // 如果用户指定了Service的value值，就用指定的value值
                    if (!"".equals(beanName.trim())) {
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }
                    // 如果没有设置，就按照接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> anInterface : interfaces) {
                        ioc.put(anInterface.getSimpleName(), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    /*
     * 传入的字符串首字母小写
     * @param simpleName
     * @return String
     * @author majian
     * @creed: Talk is cheap,show me the code
     * @date 2018/11/11 22:41
     */
    private String lowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doScanner(String scanPackage) {
        // 将所有的包路径转化为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File file = new File(url.getFile());
        for (File f : file.listFiles()) {
            // 如果是文件夹，继续递归
            if (f.isDirectory()) {
                doScanner(scanPackage + "." + f.getName());
            } else {
                classNames.add(scanPackage + "." + f.getName().replace(".class", "").trim());
            }
        }
    }

    private void doLoadConfig(String initParameter) {
        try (BufferedInputStream bis = (BufferedInputStream) this.getClass().getClassLoader().getResourceAsStream(initParameter)) {
            properties.load(bis);
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
