import { registerHooks } from 'node:module';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

// 使用项目现有TypeScript依赖运行Node测试，不为纯业务函数引入额外测试框架。
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier.startsWith('dayjs/plugin/') && !specifier.endsWith('.js')) {
      return nextResolve(specifier + '.js', context);
    }
    if (specifier.startsWith('.') && context.parentURL?.startsWith('file:')) {
      for (const suffix of ['.ts', '.tsx']) {
        const candidate = new URL(specifier + suffix, context.parentURL);
        if (existsSync(candidate)) return nextResolve(candidate.href, context);
      }
    }
    return nextResolve(specifier, context);
  },
  load(url, context, nextLoad) {
    if (/\.tsx?$/.test(url) && !url.includes('/node_modules/')) {
      return { format: 'module', shortCircuit: true,
        source: ts.transpileModule(readFileSync(fileURLToPath(url), 'utf8'), {
          compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2023, jsx: ts.JsxEmit.ReactJSX },
        }).outputText };
    }
    return nextLoad(url, context);
  },
});
