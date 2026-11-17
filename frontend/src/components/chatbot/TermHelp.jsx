import React from "react";
import { CircleHelp } from "lucide-react";
import { faqIdForTerm } from "../../api/faq-service.js";
import { useFaq } from "./FaqContext.jsx";
export default function TermHelp({ term }) {
  const { openFaq } = useFaq();
  const id = faqIdForTerm(term);
  if (!id) return null;
  return (
    <button
      type="button"
      className="tax-term-help"
      aria-label={`${term} 용어 설명`}
      title={`${term} 용어 설명`}
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        openFaq(id);
      }}
    >
      <CircleHelp size={14} />
    </button>
  );
}
