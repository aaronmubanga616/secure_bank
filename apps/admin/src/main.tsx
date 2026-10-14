import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './styles.css';
const client = new QueryClient();
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><QueryClientProvider client={client}><BrowserRouter><main><p>SECUREBANK / OPERATIONS</p><h1>A complete view.<br/>A considered response.</h1><p>Restricted staff workspace.</p><small>Educational banking simulator</small></main></BrowserRouter></QueryClientProvider></React.StrictMode>,
);
