#!/usr/bin/env python3
"""
Sistema de delegación y monitoreo de tareas para Jules.
Crea tareas en Jules, monitorea su progreso y reenvía resultados.
"""

import json
import time
import subprocess
import os
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

class JulesTaskManager:
    def __init__(self, workspace_dir: str = "/home/eli/Documentos/fenix-mobile-mcp"):
        self.workspace_dir = workspace_dir
        self.tasks_file = Path(workspace_dir) / ".jules_tasks.json"
        self.log_file = Path(workspace_dir) / ".jules_monitor.log"
        self.tasks = self.load_tasks()
    
    def load_tasks(self) -> List[Dict]:
        if self.tasks_file.exists():
            return json.loads(self.tasks_file.read_text())
        return []
    
    def save_tasks(self):
        self.tasks_file.write_text(json.dumps(self.tasks, indent=2))
    
    def log(self, msg: str):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        line = f"[{timestamp}] {msg}"
        print(line)
        with open(self.log_file, "a") as f:
            f.write(line + "\n")
    
    def create_task(self, title: str, instructions: str, labels: List[str] = None) -> str:
        """Crea una nueva tarea en Jules usando gh CLI"""
        task_id = f"jules-{int(time.time())}"
        
        # Crear issue en GitHub para Jules
        labels_str = ",".join(labels) if labels else "jules,bug"
        result = subprocess.run([
            "gh", "issue", "create",
            "--title", f"[JULES] {title}",
            "--body", instructions,
            "--label", labels_str,
            "--repo", "eli/fenix-mobile-mcp"  # Ajustar al repo real
        ], capture_output=True, text=True, cwd=self.workspace_dir)
        
        if result.returncode == 0:
            issue_url = result.stdout.strip()
            issue_number = issue_url.split("/")[-1]
            task = {
                "id": task_id,
                "issue_number": issue_number,
                "title": title,
                "instructions": instructions,
                "labels": labels,
                "status": "created",
                "created_at": datetime.now().isoformat(),
                "updated_at": datetime.now().isoformat(),
                "jules_assigned": True
            }
            self.tasks.append(task)
            self.save_tasks()
            self.log(f"✅ Tarea creada: #{issue_number} - {title}")
            return issue_number
        else:
            self.log(f"❌ Error creando tarea: {result.stderr}")
            return None
    
    def check_task_progress(self, issue_number: str) -> Dict:
        """Verifica el progreso de una tarea en Jules"""
        result = subprocess.run([
            "gh", "issue", "view", issue_number,
            "--json", "number,title,state,labels,comments,updatedAt",
            "--repo", "eli/fenix-mobile-mcp"
        ], capture_output=True, text=True, cwd=self.workspace_dir)
        
        if result.returncode == 0:
            return json.loads(result.stdout)
        return {}
    
    def wait_for_completion(self, issue_number: str, check_interval: int = 60, max_wait: int = 3600) -> Dict:
        """Espera a que Jules complete la tarea"""
        self.log(f"⏳ Esperando completación de #{issue_number}...")
        start_time = time.time()
        
        while time.time() - start_time < max_wait:
            progress = self.check_task_progress(issue_number)
            if not progress:
                time.sleep(check_interval)
                continue
            
            # Buscar comentarios de Jules (bot)
            comments = progress.get("comments", [])
            jules_comments = [c for c in comments if "jules" in c.get("author", {}).get("login", "").lower()]
            
            if jules_comments:
                latest = jules_comments[-1]
                self.log(f"📝 Jules comentó en #{issue_number}: {latest['body'][:200]}...")
                
                # Verificar si dice que terminó
                body_lower = latest["body"].lower()
                if any(keyword in body_lower for keyword in ["completed", "finished", "done", "resuelto", "completado", "listo"]):
                    self.log(f"✅ Jules completó #{issue_number}")
                    return {"status": "completed", "comment": latest["body"]}
            
            # Verificar si el issue se cerró
            if progress.get("state") == "CLOSED":
                self.log(f"✅ Issue #{issue_number} cerrado")
                return {"status": "closed", "progress": progress}
            
            self.log(f"🔄 #{issue_number} aún en progreso... (estado: {progress.get('state')})")
            time.sleep(check_interval)
        
        return {"status": "timeout", "message": f"Timeout después de {max_wait}s"}
    
    def assign_fix_task(self, bug_description: str, files_to_fix: List[str], expected_behavior: str) -> str:
        """Crea una tarea específica para fixear un bug"""
        instructions = f"""
## Bug a fixear
{bug_description}

## Archivos a modificar
{chr(10).join(f"- `{f}`" for f in files_to_fix)}

## Comportamiento esperado
{expected_behavior}

## Contexto técnico
- El sandbox Linux usa proot con Debian 12 arm64
- El PTY bridge (Python) corre dentro de proot y hace fork+exec
- El problema es que el PATH no incluye /root/.opencode/bin al ejecutar comandos vía PTY
- La instalación de OpenCode funciona via comando directo (non-PTY) pero falla en PTY

## Instrucciones para Jules
1. Analiza el código en los archivos indicados
2. Identifica la causa raíz
3. Implementa la fix mínima
4. Verifica que no rompa nada más
5. Comenta en el issue cuando termines con "COMPLETADO: <resumen>"
"""
        return self.create_task(
            title=f"Fix: {bug_description[:50]}",
            instructions=instructions,
            labels=["jules", "bug", "sandbox", "pty"]
        )


def main():
    manager = JulesTaskManager()
    
    # Tareas a delegar a Jules basadas en los bugs identificados
    tasks_to_create = [
        {
            "bug": "PTY bridge no ve /root/.opencode/bin - PATH no incluye el directorio al ejecutar comandos",
            "files": [
                "app/src/main/kotlin/com/fenix/mobilemcp/sandbox/SandboxInstaller.kt",
                "app/src/main/kotlin/com/fenix/mobilemcp/sandbox/SandboxManager.kt"
            ],
            "expected": "El PTY bridge debe hacer `source /etc/profile` antes de ejecutar el comando para que PATH incluya /root/.opencode/bin y /root/.local/bin"
        },
        {
            "bug": "DNS falla en scripts directos - curl no resuelve hosts",
            "files": [
                "app/src/main/kotlin/com/fenix/mobilemcp/sandbox/SandboxManager.kt"
            ],
            "expected": "refreshDns() debe llamarse antes de cualquier comando curl en proot, no solo en installOpenCode"
        },
        {
            "bug": "Autocorrect/autocap en terminal input field rompe comandos via adb input text",
            "files": [
                "app/src/main/kotlin/com/fenix/mobilemcp/ui/screens/sandbox/SandboxConsoleScreen.kt"
            ],
            "expected": "KeyboardOptions debe tener autoCorrectEnabled=false y capitalization=KeyboardCapitalization.None"
        },
        {
            "bug": "URL del servidor se corta en NetworkServerPanel",
            "files": [
                "app/src/main/kotlin/com/fenix/mobilemcp/ui/components/NetworkServerPanel.kt"
            ],
            "expected": "OutlinedTextField debe tener singleLine=true, maxLines=1 para evitar clipping"
        }
    ]
    
    created_issues = []
    for task in tasks_to_create:
        issue_num = manager.assign_fix_task(task["bug"], task["files"], task["expected"])
        if issue_num:
            created_issues.append(issue_num)
            time.sleep(2)  # Rate limiting
    
    manager.log(f"📋 Creadas {len(created_issues)} tareas: {created_issues}")
    
    # Monitorear cada tarea
    for issue_num in created_issues:
        result = manager.wait_for_completion(issue_num, check_interval=120, max_wait=7200)
        manager.log(f"📊 Resultado #{issue_num}: {result}")
        
        # Actualizar estado en tasks.json
        for task in manager.tasks:
            if task.get("issue_number") == issue_num:
                task["status"] = result.get("status", "unknown")
                task["result"] = result
                task["updated_at"] = datetime.now().isoformat()
        manager.save_tasks()
    
    manager.log("🏁 Monitoreo completado para todas las tareas")


if __name__ == "__main__":
    main()