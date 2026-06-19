# Folio 渲染测试文档 · test.md

> 这份样例参考 [younghz/Markdown](https://github.com/younghz/Markdown) 的"全语法"思路,并补上 **极端边界**(超长行 / 超宽表 / 长公式 / 多语言代码),用来检验 Folio 的 Markdown 渲染、代码高亮、横向滚动与阅读主题。

---

## 1. 标题(六级)

# 一级标题 H1
## 二级标题 H2
### 三级标题 H3
#### 四级标题 H4
##### 五级标题 H5
###### 六级标题 H6

---

## 2. 强调与行内样式

普通文本。*斜体 italic* 与 _另一种斜体_。**加粗 bold** 与 __另一种加粗__。***粗斜体***。~~删除线 strikethrough~~。

行内代码:`val x = 42`、`git commit -m "msg"`、`Ctrl + Shift + P`。

行内公式(注意:Folio 仅支持 `$$` 块公式,行内 `$a^2$` 会原样显示,这是已知取舍)。

---

## 3. 列表

无序列表(三种符号都应渲染为圆点):

- 苹果
+ 香蕉
* 橘子

有序列表与嵌套:

1. 第一步
2. 第二步
   1. 子步骤 a
   2. 子步骤 b
      - 更深一层
      - 再来一条
3. 第三步

任务列表:

- [x] 已完成:搭好渲染管线
- [x] 已完成:代码高亮
- [ ] 待办:轻量编辑
- [ ] 待办:图片放大

---

## 4. 引用(含多级嵌套)

> 一级引用:Markdown 的设计哲学是"易读易写"。
>
> > 二级引用:纯文本即可成文。
> >
> > > 三级引用:渲染后依然层次分明。

---

## 5. 代码块(多语言,喂语法高亮)

Python:

```python
# 快速排序
def quicksort(arr):
    if len(arr) <= 1:
        return arr
    pivot = arr[len(arr) // 2]
    left = [x for x in arr if x < pivot]
    mid = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    return quicksort(left) + mid + quicksort(right)

print(quicksort([3, 6, 1, 8, 2, 9]))  # [1, 2, 3, 6, 8, 9]
```

JavaScript:

```javascript
const fib = (n) => {
  let [a, b] = [0, 1];
  for (let i = 0; i < n; i++) {
    [a, b] = [b, a + b];   // 解构交换
  }
  return a; // 返回第 n 个斐波那契数
};
console.log(`fib(10) = ${fib(10)}`);
```

C++:

```cpp
#include <iostream>
#include <vector>
using namespace std;

int main() {
    vector<int> v = {5, 3, 8, 1};
    for (int& x : v) x *= 2;          // 每个元素翻倍
    for (auto x : v) cout << x << " "; // 10 6 16 2
    return 0;
}
```

SQL:

```sql
SELECT user_id, COUNT(*) AS cnt
FROM orders
WHERE created_at >= '2026-01-01'   -- 今年的订单
GROUP BY user_id
HAVING COUNT(*) > 3
ORDER BY cnt DESC;
```

无语言标注(应回退为纯等宽,不报错):

```
just plain text
no language here
```

超长行代码(测横向滚动,不应换行而应可左右滑):

```python
result = some_very_long_function_name(argument_one, argument_two, argument_three, argument_four, argument_five, argument_six, keyword_arg="a fairly long string value that keeps going and going")
```

---

## 6. 表格

普通表格(含对齐):

| 语言 | 类型 | 首次发布 |
|:-----|:----:|--------:|
| Python | 动态 | 1991 |
| C++ | 静态 | 1985 |
| Rust | 静态 | 2010 |

超宽表格(测横向滚动):

| 序号 | 模块 | 负责人 | 状态 | 优先级 | 预计工时 | 实际工时 | 备注说明（较长） | 关联需求 | 上线日期 |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 渲染引擎 | 张三 | 进行中 | 高 | 16h | 12h | 代码高亮与排版精修,需覆盖多语言 | REQ-001 | 2026-07-01 |
| 2 | 文件管理 | 李四 | 待开始 | 中 | 8h | 0h | 扁平分组,非真实文件夹树 | REQ-002 | 2026-07-08 |
| 3 | 编辑器 | 王五 | 待开始 | 中 | 12h | 0h | 仅 Markdown,写回库内副本 | REQ-003 | 2026-07-15 |

---

## 7. 数学公式($$ 块)

行间公式:

$$
E = mc^2
$$

长公式(测横向滚动):

$$
f(x) = a_0 + \sum_{n=1}^{\infty} \left( a_n \cos\frac{n\pi x}{L} + b_n \sin\frac{n\pi x}{L} \right) = a_0 + a_1\cos\frac{\pi x}{L} + b_1\sin\frac{\pi x}{L} + a_2\cos\frac{2\pi x}{L} + \cdots
$$

矩阵:

$$
A = \begin{pmatrix} 1 & 2 & 3 \\ 4 & 5 & 6 \\ 7 & 8 & 9 \end{pmatrix}
$$

---

## 8. 链接与图片

行内链接:[Folio 项目](https://example.com "标题提示")。

引用式链接:[Markdown 指南][md-guide] 和 [Anthropic][ai]。

自动链接(linkify):https://www.example.com

内嵌图片(data-URI,无网络也能显示;批 2 起可点击放大):

![示例图片](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHgAAABQCAIAAABd+SbeAAADvUlEQVR4nO3da0iTURzH8f+ZBhmZWlSPGmqGa2TZxUxbi1hRSEQYeaGigoLI7nQx60VRL4puRtmLim4YUaIlBJH0ptTKdFlZqUu0lpdNpcjZbeZ6FnMhkVtWyN/Lfp8XMs8Oh4evh+MGz5hQxe0XREII0f5DYX+AX0X31yBggdBMEJoJQjNBaCYIzQShmXi6euJJ1k6ua+hXck5VOR3Hju7pHe0QlXQEbxTF3733S9ik/ENJ7GgmCM0EoZkgNBOEZoLQTBCaCUIzQWgmCM0EoZkgNBOEZoLQTBCaCUIzQWgmCM0EoZkgNBOEZoLQTBC6d9zX0aXb55P1rxsFCSJ69MxwI7f0twlX01cs3Xg5KMAvXCndydO7Wuf0kfjkHdlEdCYtvvSl8cylR47xVcujJk8M3JJyk4hmxISoY0Jk2fb1a1tWdqm5pZXcJ7TVKm89kPPrDSVO1Rg/1Jmaxd8tKI30ViiETbYJQcOHDbZaZSJSKUdETPA/nl4gy7a5s8OSEiedPVdE7hP6N34+g7at1g4cOMDSaj15Mb/Z/KXjqcsnlq3YfMV3iNe6lRov+4S2sxmF5o+Wzou8rW0OCRr6xvB+VKBvndEsSd5ENEcbdut2xffvshDi/kNDSLBf+x+D3PSMXrNEfa+oKuXgzbyiqlWJ0Z0nrEyIeqB7s/do7kOdYdniSKeLvKxoCFeNJKJwlVSub3QM+kve9Saz43Frq/XCJZ0s24jcJrSnpyJt96K03YuO7YobJflGqALyi6uJqKD4dYQqoPP8cKVU+NhgP9BL3o4bKzlds0zfMG6sPbQqbHhFZZNj0EPx81K1s8ZsSFanpmipT+n2M7qrc1h0fVB//vJNttmG+g4iIoulzTHY9O5ToL9PTW3z3bzqYl3t/j3zqE/p5qOjVF+viQolIs3U0S9eGTtPKHtliokMJqLoKUHllQ2u1imraIhbML5jOxPRg0LD/FiVh4f9gjVq+2sPcufQ564Vzp6uPLxroXZ62IVM+6sCY2NL/PyJHRMyrj+eGR26b3usZlroleslrtZ5XmaKnhr8otzUMaIrqamrN6du165fqza3WGS57/wfbCdcfXL2aVYq7o9W/Pv90TmnqvDJ2Z6Et+BMEJoJQjNBaCYIzQShmSA0E4RmgtBMEJoJQjNBaCYIzQShmSA0E4RmgtBMEJoJQjNBaCYIzQShmSA0E4RmgtBMELp33Lary9zBdSX9HHZ0T+/oKQmH8LUsiv/64hWnPbGjmSA0E4RmgtBMEJoJQjNBaOLxA9QyE6Z+IA6MAAAAAElFTkSuQmCC)

---

## 9. 分割线

三种写法都应渲染为一条横线:

***

---

___

---

## 10. 极端边界:超长段落行

这是一个故意写得非常长的段落,用来检验自动换行是否正常、阅读主题下行距是否舒适、以及超长内容不会撑破布局或导致崩溃。这是一个故意写得非常长的段落,用来检验自动换行是否正常、阅读主题下行距是否舒适、以及超长内容不会撑破布局或导致崩溃。这是一个故意写得非常长的段落,用来检验自动换行是否正常。

---

## 11. 脚注(已知未支持)

Markwon 未装脚注插件,下面这行会**原样显示**,这是预期内的取舍:这里有一个脚注[^1]。

[^1]: 这是脚注内容,当前版本不渲染为上标跳转。

[md-guide]: https://github.com/younghz/Markdown
[ai]: https://www.anthropic.com

---

*测试文档结束。若以上各节都正确渲染、超宽表/长公式/超长代码行可横滑、代码按语言高亮、切换阅读主题与代码配色即时生效,则批 1 通过。*
