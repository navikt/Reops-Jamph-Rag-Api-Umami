# GitHub Copilot instructions

----
applyTo: '**'
---

Do:
- Be direct and concise in your response
- Write code for clarity, not cleverness.
- Follow existing code patterns in the project. And clean code principles.
- Understand that Jamph-Rag-Api-Umami is an API service for Umami Analytics using RAG (Retrieval-Augmented Generation) techniques.
- The root folder of the project is "Jamph-Rag-Api-Umami".
- Ollama is here https://jamph-ollama.ekstern.dev.nav.no/ use Routes.ollamaUrl to access it.
- Prefer minimal, focused changes.
- Avoid guessing when required context is missing.
- When changing a resource that is used by other parts of the codebase, clarify the impact of the change and ask for approval before proceeding.

Don't:
- Add code comments unless explicitly asked to.
- Create documentation files unless explicitly asked to.
- Use verbose and overly polite language.
- Introduce new dependencies without approval.
- Use local resources or files that are not part of the project.
- Use localhost, local dockers, or any local services in your code.