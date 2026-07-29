# CI/CD 操作流程

## 提交代码

```powershell
git add <文件路径>
git commit -m "提交信息"
git remote set-url origin "https://${env:LOGFOX_TOKEN}@github.com/fvgfgtxdeujv/LogFox.git"
git push origin master
git remote set-url origin "https://github.com/fvgfgtxdeujv/LogFox.git"
```

---

## 操作流程

1. 修改代码后，等待用户检查确认
2. 用户确认后执行 `git push` 触发 CI
3. 推送完成后停止操作，等待用户输入 "1"
4. 用户输入 "1" = 构建失败，下载构建日志分析报错
5. 修复代码后直接推送（不进行本地构建）
6. 重复步骤 3-5 直到构建成功

---

## 下载构建日志

```powershell
$token = (Get-Content "$HOME\.claude\.env" | Select-String "LogFox-Cli").ToString().Split('=')[1]
$runId = <运行 ID>

Invoke-WebRequest -Uri "https://api.github.com/repos/fvgfgtxdeujv/LogFox/actions/runs/$runId/logs" `
  -OutFile "C:\Users\q\Desktop\build_logs_$runId.zip" `
  -Headers @{Authorization = "token $token"}

Expand-Archive -Path "C:\Users\q\Desktop\build_logs_$runId.zip" -DestinationPath "C:\temp\build_logs_$runId" -Force
```

---

## 快速定位错误

```python
import os

for root, dirs, files in os.walk('C:/temp/build_logs'):
    for f in files:
        if f.endswith('.txt'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8', errors='replace') as fh:
                lines = fh.readlines()
            errors = [(i, l.rstrip()) for i, l in enumerate(lines) if l.strip().startswith('e:')]
            if errors:
                print('=== ' + path + ' ===')
                for idx, line in errors[:20]:
                    print('%d: %s' % (idx, line))
```
