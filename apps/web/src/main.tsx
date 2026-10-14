import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './styles.css';

const queryClient = new QueryClient();
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><QueryClientProvider client={queryClient}><BrowserRouter>
    <main><p>SECUREBANK / ZAMBIA</p><h1>Your money.<br/>A clearer perspective.</h1><p>Customer banking, coming together.</p><small>Educational simulator · ZMW only</small></main>
  </BrowserRouter></QueryClientProvider></React.StrictMode>,
);
