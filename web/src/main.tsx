import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ScribbleApp } from "./scribble/ScribbleApp";
import "./styles.css";

createRoot(document.getElementById("root") as HTMLElement).render(
  <StrictMode>
    <ScribbleApp />
  </StrictMode>,
);
