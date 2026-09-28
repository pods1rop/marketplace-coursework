"""Генерирует JMeter-план нагрузочного тестирования маркетплейса (4 сценария)."""
import sys
from xml.sax.saxutils import escape

OUT = sys.argv[1]
CATEGORIES = sys.argv[2]   # "17,15,14"
PRODUCTS = sys.argv[3]     # "13,14,..."

SCENARIOS = [  # имя, потоков, итераций, ramp-up
    ("Smoke", 1, 10, 1),
    ("Load", 5, 20, 5),
    ("Multithread", 20, 50, 10),
    ("Stress", 50, 100, 20),
]


def prop(name, value, kind="stringProp"):
    return f'<{kind} name="{name}">{escape(str(value))}</{kind}>'


def args(params):
    items = "".join(
        f'''<elementProp name="{n}" elementType="HTTPArgument">
              <boolProp name="HTTPArgument.always_encode">true</boolProp>
              {prop("Argument.value", v)}
              {prop("Argument.metadata", "=")}
              <boolProp name="HTTPArgument.use_equals">true</boolProp>
              {prop("Argument.name", n)}
            </elementProp>''' for n, v in params)
    return f'''<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" enabled="true">
          <collectionProp name="Arguments.arguments">{items}</collectionProp>
        </elementProp>'''


def sampler(label, method, path, params=(), follow=True, children=""):
    return f'''<HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="{escape(label)}" enabled="true">
        {args(params)}
        {prop("HTTPSampler.path", path)}
        {prop("HTTPSampler.method", method)}
        <boolProp name="HTTPSampler.follow_redirects">{"true" if follow else "false"}</boolProp>
        <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
        <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
        {prop("HTTPSampler.contentEncoding", "UTF-8")}
      </HTTPSamplerProxy>
      <hashTree>{children}</hashTree>'''


def regex(var, expr):
    return f'''<RegexExtractor guiclass="RegexExtractorGui" testclass="RegexExtractor" testname="{var}" enabled="true">
          {prop("RegexExtractor.useHeaders", "false")}
          {prop("RegexExtractor.refname", var)}
          {prop("RegexExtractor.regex", expr)}
          {prop("RegexExtractor.template", "$1$")}
          {prop("RegexExtractor.default", "NOT_FOUND")}
          {prop("RegexExtractor.match_number", "1")}
        </RegexExtractor>
        <hashTree/>'''


def assertion(name, field, pattern, test_type):
    # test_type: 2 = contains, 6 = not contains (2|4), 16 = substring, 20 = not substring
    return f'''<ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="{escape(name)}" enabled="true">
          <collectionProp name="Asserion.test_strings">{prop("0", pattern)}</collectionProp>
          {prop("Assertion.custom_message", "")}
          {prop("Assertion.test_field", field)}
          <boolProp name="Assertion.assume_success">false</boolProp>
          <intProp name="Assertion.test_type">{test_type}</intProp>
        </ResponseAssertion>
        <hashTree/>'''


PRE = f'''<JSR223PreProcessor guiclass="TestBeanGUI" testclass="JSR223PreProcessor" testname="Случайная категория и товар" enabled="true">
          {prop("scriptLanguage", "groovy")}
          {prop("parameters", "")}
          {prop("filename", "")}
          {prop("cacheKey", "true")}
          {prop("script", f"def cats = [{CATEGORIES}]; def prods = [{PRODUCTS}]; def r = new Random(); vars.put('cat', cats[r.nextInt(cats.size())].toString()); vars.put('pid', prods[r.nextInt(prods.size())].toString())")}
        </JSR223PreProcessor>
        <hashTree/>'''


def scenario_samplers():
    return "".join([
        sampler("GET /auth (CSRF-токен)", "GET", "/auth", children=regex("csrf", 'name="_csrf" value="([^"]+)"')),
        sampler("POST /auth/login", "POST", "/auth/login",
                [("username", "${username}"), ("password", "${password}"), ("_csrf", "${csrf}")], follow=False,
                children=assertion("Вход без ошибки", "Assertion.response_headers", "error", 20)
                         + assertion("Код 302", "Assertion.response_code", "302", 8)),
        sampler("GET /catalog?categoryId", "GET", "/catalog", [("categoryId", "${__Random(" + CATEGORIES + ")}")],
                children=assertion("Каталог отрисован", "Assertion.response_data", "catalog-content", 16)),
        sampler("GET /product/{id}", "GET", "/product/${__Random(" + PRODUCTS + ",pid)}",
                children=regex("csrf2", 'name="_csrf" value="([^"]+)"')
                         + assertion("Страница товара", "Assertion.response_code", "200", 8)),
        sampler("POST /cart/add/{id}", "POST", "/cart/add/${pid}", [("_csrf", "${csrf2}")], follow=False,
                children=assertion("Редирект в корзину", "Assertion.response_headers", "/cart", 16)),
        sampler("GET /orders", "GET", "/orders",
                children=assertion("Список заказов", "Assertion.response_code", "200", 8)),
    ])


def thread_group(name, threads, loops, ramp):
    return f'''<ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="{name}" enabled="true">
        {prop("ThreadGroup.on_sample_error", "continue")}
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" enabled="true">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          {prop("LoopController.loops", loops)}
        </elementProp>
        {prop("ThreadGroup.num_threads", threads)}
        {prop("ThreadGroup.ramp_time", ramp)}
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <CookieManager guiclass="CookiePanel" testclass="CookieManager" testname="Cookies" enabled="true">
          <collectionProp name="CookieManager.cookies"/>
          <boolProp name="CookieManager.clearEachIteration">true</boolProp>
        </CookieManager>
        <hashTree/>
        <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="Пауза 100–300 мс" enabled="true">
          {prop("ConstantTimer.delay", "100")}
          {prop("RandomTimer.range", "200")}
        </UniformRandomTimer>
        <hashTree/>
        {scenario_samplers()}
      </hashTree>'''


plan = f'''<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="MARKETPLACE — нагрузочное тестирование" enabled="true">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">true</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" enabled="true">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="HTTP Defaults" enabled="true">
        <elementProp name="HTTPsampler.Arguments" elementType="Arguments"><collectionProp name="Arguments.arguments"/></elementProp>
        {prop("HTTPSampler.domain", "${__P(host,localhost)}")}
        {prop("HTTPSampler.port", "${__P(port,8080)}")}
        {prop("HTTPSampler.protocol", "http")}
        {prop("HTTPSampler.implementation", "HttpClient4")}
      </ConfigTestElement>
      <hashTree/>
      <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="Тестовые пользователи" enabled="true">
        {prop("filename", "${__P(users,users.csv)}")}
        {prop("fileEncoding", "UTF-8")}
        {prop("variableNames", "")}
        <boolProp name="ignoreFirstLine">false</boolProp>
        {prop("delimiter", ",")}
        <boolProp name="quotedData">false</boolProp>
        <boolProp name="recycle">true</boolProp>
        <boolProp name="stopThread">false</boolProp>
        {prop("shareMode", "shareMode.all")}
      </CSVDataSet>
      <hashTree/>
      {"".join(thread_group(*s) for s in SCENARIOS)}
    </hashTree>
  </hashTree>
</jmeterTestPlan>
'''
open(OUT, "w", encoding="utf-8").write(plan)
print("written", OUT)
